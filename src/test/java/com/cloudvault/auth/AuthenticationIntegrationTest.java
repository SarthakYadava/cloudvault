package com.cloudvault.auth;

import com.cloudvault.file.StoredFile;
import com.cloudvault.file.StoredFileRepository;
import com.cloudvault.storage.ObjectStorage;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private StoredFileRepository fileRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    @BeforeEach
    void cleanDatabase() {
        fileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationReturnsSignedTokenAndNormalizesEmail() throws Exception {
        String response = register(
                "Sarthak",
                "Sarthak@Example.com",
                "StrongPass123"
        );
        JsonNode json = objectMapper.readTree(response);

        assertThat(json.path("accessToken").asText()).isNotBlank();
        assertThat(json.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.path("expiresInSeconds").asLong()).isEqualTo(3600);
        assertThat(json.path("user").path("email").asText())
                .isEqualTo("sarthak@example.com");

        UserAccount saved = userRepository.findByEmail("sarthak@example.com")
                .orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("StrongPass123");
        assertThat(saved.getPasswordHash()).startsWith("$2");
    }

    @Test
    void protectedEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required to access this resource."));
    }

    @Test
    void userSeesOnlyTheirOwnFiles() throws Exception {
        String firstToken = token(register(
                "First User",
                "first@example.com",
                "StrongPass123"
        ));
        String secondToken = token(register(
                "Second User",
                "second@example.com",
                "StrongPass123"
        ));

        UserAccount first = userRepository.findByEmail("first@example.com")
                .orElseThrow();
        UserAccount second = userRepository.findByEmail("second@example.com")
                .orElseThrow();
        fileRepository.save(StoredFile.createAvailable(
                first.getId(),
                "first.pdf",
                "users/" + first.getId() + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                100
        ));
        fileRepository.save(StoredFile.createAvailable(
                second.getId(),
                "second.pdf",
                "users/" + second.getId() + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                200
        ));

        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].originalName").value("first.pdf"));

        assertThat(secondToken).isNotBlank();
    }

    @Test
    void userCannotDownloadAnotherUsersFile() throws Exception {
        register("Owner", "owner@example.com", "StrongPass123");
        String otherToken = token(register(
                "Other User",
                "other@example.com",
                "StrongPass123"
        ));
        UserAccount owner = userRepository.findByEmail("owner@example.com")
                .orElseThrow();
        StoredFile file = fileRepository.save(StoredFile.createAvailable(
                owner.getId(),
                "private.pdf",
                "users/" + owner.getId() + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                100
        ));

        mockMvc.perform(get("/api/files/{id}/download", file.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        verifyNoInteractions(objectStorage);
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        register("Sarthak", "sarthak@example.com", "StrongPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "sarthak@example.com",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("The email address or password is incorrect."));
    }

    private String register(String name, String email, String password) throws Exception {
        String request = objectMapper.writeValueAsString(
                new RegisterRequest(name, email, password)
        );
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String token(String response) throws Exception {
        return objectMapper.readTree(response).path("accessToken").asText();
    }
}
