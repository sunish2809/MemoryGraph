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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "memorygraph.registration.invite-code=beta-sikkim")
class InviteRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationOptionsAdvertiseTheInvite() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/auth/registration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteRequired").value(true));
    }

    @Test
    void registerWithoutTheInviteIsRejected() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-invite-%d@example.com","password":"correct-horse-battery","displayName":"Beta"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"));
    }

    @Test
    void registerWithTheInviteCreatesTheAccount() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invited-%d@example.com","password":"correct-horse-battery","displayName":"Beta","inviteCode":"beta-sikkim"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
