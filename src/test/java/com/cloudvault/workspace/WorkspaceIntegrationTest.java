package com.cloudvault.workspace;

import com.cloudvault.auth.RegisterRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

        mockMvc.perform(patch(
                                "/api/workspaces/{workspaceId}/requests/{requestId}",
                                workspaceId,
                                requestId
                        )
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUBMITTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

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
