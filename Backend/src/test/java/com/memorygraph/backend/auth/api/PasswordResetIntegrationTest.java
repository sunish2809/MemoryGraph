package com.memorygraph.backend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.auth.application.AuthService;
import com.memorygraph.backend.auth.application.CapturedPasswordResetTokens;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturedPasswordResetTokens capturedResetTokens;

    @Test
    void forgotPasswordIsIdenticalForUnknownAndKnownEmails() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-%s@example.com"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value(AuthService.FORGOT_PASSWORD_ACK));
    }

    @Test
    void resetLinkChangesThePasswordAndInvalidatesTheOldToken() throws Exception {
        String email = "reset-" + System.nanoTime() + "@example.com";
        String oldPassword = "correct-horse-battery";
        String newPassword = "new-correct-battery";

        MvcResult registration = mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, oldPassword)))
                .andExpect(status().isCreated())
                .andReturn();
        String previousAccessToken = JsonPath.read(registration.getResponse().getContentAsString(),
                "$.data.accessToken");

        mockMvc.perform(post(ApiPaths.V1 + "/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(AuthService.FORGOT_PASSWORD_ACK));

        String resetToken = capturedResetTokens.lastToken(email);
        org.assertj.core.api.Assertions.assertThat(resetToken).isNotBlank();

        mockMvc.perform(post(ApiPaths.V1 + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(resetToken, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email, oldPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(get(ApiPaths.V1 + "/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + previousAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(ApiPaths.V1 + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(resetToken, "another-valid-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void garbageResetTokenIsRejected() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"this-is-not-a-real-reset-token","password":"new-correct-battery"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
    }

    private static String registerPayload(String email, String password) {
        return """
                {"email":"%s","password":"%s","displayName":"Test Person"}
                """.formatted(email, password);
    }

    private static String loginPayload(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
