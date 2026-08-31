package com.memorygraph.backend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestcontainersConfiguration;

/**
 * End-to-end coverage of the authentication contract: the happy path, the error envelope, and the
 * guarantee that protected endpoints reject unauthenticated callers.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerIssuesTokenAndProfileIsReadableWithIt() throws Exception {
        String email = uniqueEmail();

        MvcResult registration = mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String accessToken = readAccessToken(registration);

        mockMvc.perform(get(ApiPaths.V1 + "/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.displayName").value("Test Person"));
    }

    @Test
    void loginReturnsAFreshTokenForValidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery");

        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email, "correct-horse-battery")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void emailIsTreatedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery");

        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email.toUpperCase(), "correct-horse-battery")))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery");

        mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, "correct-horse-battery")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidPayloadReportsFieldErrors() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists())
                .andExpect(jsonPath("$.error.fieldErrors.password").exists())
                .andExpect(jsonPath("$.error.fieldErrors.displayName").exists());
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingWhy() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery");

        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email, "not-the-right-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmailProducesTheSameErrorAsAWrongPassword() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("nobody-" + System.nanoTime() + "@example.com", "some-password-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRequiresAToken() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedEndpointRejectsATamperedToken() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.tampered.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("memorygraph-backend"));
    }

    @Test
    void registrationIsOpenWhenNoInviteIsConfigured() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/auth/registration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteRequired").value(false));
    }

    @Test
    void unknownRouteReturnsTheStandardEnvelope() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private static String readAccessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private static String uniqueEmail() {
        return "user-" + System.nanoTime() + "@example.com";
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
