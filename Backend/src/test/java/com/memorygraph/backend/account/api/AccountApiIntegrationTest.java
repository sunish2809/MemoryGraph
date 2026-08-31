package com.memorygraph.backend.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiIntegrationTest {

    private static final String ACCOUNT = ApiPaths.V1 + "/account";
    private static final String MEMORIES = ApiPaths.V1 + "/memories";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void privacyDescribesALocalDeploymentWithoutALanguageModel() throws Exception {
        mockMvc.perform(asOwner(get(ACCOUNT + "/privacy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStoredLocally").value(true))
                .andExpect(jsonPath("$.data.storageBackend").value("LOCAL"))
                .andExpect(jsonPath("$.data.languageModelEnabled").value(false))
                .andExpect(jsonPath("$.data.languageModel").value("none"))
                .andExpect(jsonPath("$.data.embeddingsEnabled").value(false));
    }

    @Test
    void exportContainsArchiveJsonAndMediaBytes() throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"First bicycle","content":"Learned to ride in the lane."}
                                """))
                .andExpect(status().isCreated());

        MockMultipartFile file = new MockMultipartFile(
                "file", "ridge.png", "image/png", TestFixtures.pngImage(16, 16));
        mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file)).param("title", "A ridge"))
                .andExpect(status().isCreated());

        byte[] zipBytes = mockMvc.perform(asOwner(get(ACCOUNT + "/export")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Set<String> names = new HashSet<>();
        String archiveJson = null;
        boolean sawMedia = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if ("archive.json".equals(entry.getName())) {
                    archiveJson = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                } else if (entry.getName().startsWith("media/")) {
                    sawMedia = zip.readAllBytes().length > 0;
                }
            }
        }

        assertThat(names).contains("archive.json");
        assertThat(archiveJson).contains("First bicycle");
        assertThat(archiveJson).contains("memorygraph-archive-v1");
        assertThat(archiveJson).doesNotContain("users/");
        assertThat(sawMedia).isTrue();
    }

    @Test
    void deleteRequiresThePasswordAndConfirmationPhrase() throws Exception {
        mockMvc.perform(asOwner(delete(ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password","confirmation":"DELETE"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(asOwner(delete(ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s","confirmation":"please"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteWipesTheAccountAndLeavesOtherPeopleAlone() throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Gone","content":"This should vanish with the account."}
                                """))
                .andExpect(status().isCreated());

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);
        String otherMemory = mockMvc.perform(post(MEMORIES + "/text")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Stays","content":"Still here."}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String otherMemoryId = JsonPath.read(otherMemory, "$.data.id");

        mockMvc.perform(asOwner(delete(ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s","confirmation":"DELETE"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asOwner(get(ApiPaths.V1 + "/auth/me")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(MEMORIES + "/" + otherMemoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Stays"));
    }

    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(ACCOUNT + "/privacy")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ACCOUNT + "/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ACCOUNT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"x","confirmation":"DELETE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
