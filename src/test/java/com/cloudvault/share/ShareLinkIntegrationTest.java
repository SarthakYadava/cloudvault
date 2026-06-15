package com.cloudvault.share;

import com.cloudvault.audit.AuditAction;
import com.cloudvault.audit.AuditEventRepository;
import com.cloudvault.auth.RegisterRequest;
import com.cloudvault.file.StoredFile;
import com.cloudvault.file.StoredFileRepository;
import com.cloudvault.storage.ObjectStorage;
import com.cloudvault.storage.PresignedStorageUrl;
import com.cloudvault.user.UserAccount;
import com.cloudvault.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private StoredFileRepository fileRepository;

    @Autowired
    private ShareLinkRepository shareLinkRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    @BeforeEach
    void cleanDatabase() {
        shareLinkRepository.deleteAll();
        auditEventRepository.deleteAll();
        fileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsHashedLinkAndAuditsPublicAccess() throws Exception {
        String token = register("Owner", "owner@example.com");
        UserAccount owner = userRepository.findByEmail("owner@example.com").orElseThrow();
        StoredFile file = saveFile(owner, "proposal.pdf");

        String response = mockMvc.perform(post("/api/files/{id}/shares", file.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expirationMinutes": 60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String shareUrl = json.path("shareUrl").asText();
        String rawToken = shareUrl.substring(shareUrl.lastIndexOf('/') + 1);
        ShareLink stored = shareLinkRepository.findAll().getFirst();
        assertThat(stored.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(rawToken);

        when(objectStorage.createDownloadUrl(
                eq(file.getObjectKey()),
                eq(file.getOriginalName()),
                any()
        )).thenReturn(new PresignedStorageUrl(
                "https://example.test/shared-download",
                "GET",
                Map.of(),
                Instant.now().plusSeconds(60)
        ));

        mockMvc.perform(get("/s/{token}", rawToken))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.test/shared-download"));

        assertThat(auditEventRepository.findAll())
                .extracting(event -> event.getAction())
                .contains(
                        AuditAction.SHARE_LINK_CREATED,
                        AuditAction.SHARED_FILE_ACCESSED
                );
    }

    @Test
    void revokedLinkReturnsGoneAndCannotBeRevokedByAnotherUser() throws Exception {
        String ownerToken = register("Owner", "owner@example.com");
        String otherToken = register("Other", "other@example.com");
        UserAccount owner = userRepository.findByEmail("owner@example.com").orElseThrow();
        StoredFile file = saveFile(owner, "private.pdf");

        String response = mockMvc.perform(post("/api/files/{id}/shares", file.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expirationMinutes": 60}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        UUID linkId = UUID.fromString(json.path("id").asText());
        String shareUrl = json.path("shareUrl").asText();
        String rawToken = shareUrl.substring(shareUrl.lastIndexOf('/') + 1);

        mockMvc.perform(delete("/api/shares/{id}", linkId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/shares/{id}", linkId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/s/{token}", rawToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message")
                        .value("This share link has expired or been revoked."));
    }

    @Test
    void activityEndpointIsOwnerScoped() throws Exception {
        String ownerToken = register("Owner", "owner@example.com");
        String otherToken = register("Other", "other@example.com");
        UserAccount owner = userRepository.findByEmail("owner@example.com").orElseThrow();
        StoredFile file = saveFile(owner, "proposal.pdf");

        mockMvc.perform(post("/api/files/{id}/shares", file.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expirationMinutes": 60}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action")
                        .value("SHARE_LINK_CREATED"));

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void downloadUrlCreationIsCommittedToActivityHistory() throws Exception {
        String token = register("Owner", "owner@example.com");
        UserAccount owner = userRepository.findByEmail("owner@example.com").orElseThrow();
        StoredFile file = saveFile(owner, "statement.pdf");

        when(objectStorage.createDownloadUrl(
                eq(file.getObjectKey()),
                eq(file.getOriginalName()),
                any()
        )).thenReturn(new PresignedStorageUrl(
                "https://example.test/download",
                "GET",
                Map.of(),
                Instant.now().plusSeconds(60)
        ));

        mockMvc.perform(get("/api/files/{id}/download-url", file.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://example.test/download"));

        assertThat(auditEventRepository.findAll())
                .extracting(event -> event.getAction())
                .containsExactly(AuditAction.DOWNLOAD_LINK_CREATED);
    }

    private String register(String name, String email) throws Exception {
        String request = objectMapper.writeValueAsString(
                new RegisterRequest(name, email, "StrongPass123")
        );
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private StoredFile saveFile(UserAccount owner, String filename) {
        return fileRepository.save(StoredFile.createAvailable(
                owner.getId(),
                filename,
                "users/" + owner.getId() + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                100
        ));
    }
}
