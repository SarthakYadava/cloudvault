package com.cloudvault.workspace;

import com.cloudvault.auth.RegisterRequest;
import com.cloudvault.file.StoredFileRepository;
import com.cloudvault.storage.ObjectStorage;
import com.cloudvault.storage.PresignedStorageUrl;
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
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRequestRepository requestRepository;

    @Autowired
    private WorkspaceMembershipRepository membershipRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private StoredFileRepository fileRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    @BeforeEach
    void cleanDatabase() {
        requestRepository.deleteAll();
        membershipRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerAndClientCompleteDocumentRequestWorkflow() throws Exception {
        String ownerToken = register("Owner", "owner@example.com");
        String clientToken = register("Client", "client@example.com");
        String outsiderToken = register("Outsider", "outsider@example.com");

        String workspaceJson = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Legal"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Legal"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID workspaceId = UUID.fromString(
                objectMapper.readTree(workspaceJson).path("id").asText()
        );

        mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"client@example.com","role":"CLIENT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("client@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));

        String requestJson = mockMvc.perform(post(
                                "/api/workspaces/{id}/requests",
                                workspaceId
                        )
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Signed engagement letter",
                                  "description":"Upload the signed PDF.",
                                  "assigneeEmail":"client@example.com",
                                  "dueDate":"2030-01-15"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assigneeEmail").value("client@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID requestId = UUID.fromString(
                objectMapper.readTree(requestJson).path("id").asText()
        );

        mockMvc.perform(get("/api/workspaces/{id}/requests", workspaceId)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Signed engagement letter"));

        MockMultipartFile submission = new MockMultipartFile(
                "file",
                "signed-engagement.pdf",
                "application/pdf",
                "signed agreement".getBytes()
        );
        mockMvc.perform(multipart(
                                "/api/workspaces/{workspaceId}/requests/{requestId}/submission",
                                workspaceId,
                                requestId
                        )
                        .file(submission)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedFileName")
                        .value("signed-engagement.pdf"))
                .andExpect(jsonPath("$.submittedByName").value("Client"));

        String objectKey = fileRepository.findAll().getFirst().getObjectKey();
        when(objectStorage.createDownloadUrl(
                eq(objectKey),
                eq("signed-engagement.pdf"),
                any()
        )).thenReturn(new PresignedStorageUrl(
                "https://example.test/request-submission",
                "GET",
                Map.of(),
                Instant.now().plusSeconds(60)
        ));

        mockMvc.perform(get(
                                "/api/workspaces/{workspaceId}/requests/{requestId}/submission/download-url",
                                workspaceId,
                                requestId
                        )
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl")
                        .value("https://example.test/request-submission"));

        mockMvc.perform(get(
                                "/api/workspaces/{workspaceId}/requests/{requestId}/submission/download-url",
                                workspaceId,
                                requestId
                        )
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());

        UUID submittedFileId = fileRepository.findAll().getFirst().getId();
        mockMvc.perform(delete("/api/files/{id}", submittedFileId)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("This file is attached to a document request and cannot be deleted."));

        mockMvc.perform(patch(
                                "/api/workspaces/{workspaceId}/requests/{requestId}",
                                workspaceId,
                                requestId
                        )
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/workspaces/{id}/requests", workspaceId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"outsider@example.com","role":"CLIENT"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(DocumentRequestStatus.APPROVED);

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());

        assertThat(workspaceRepository.findById(workspaceId)).isEmpty();
        assertThat(membershipRepository.findAll()).isEmpty();
        assertThat(requestRepository.findAll()).isEmpty();
        assertThat(fileRepository.findAll())
                .extracting(file -> file.getOriginalName())
                .containsExactly("signed-engagement.pdf");
    }

    private String register(String name, String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(name, email, "StrongPass123")
                        )))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
