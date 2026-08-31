package com.memorygraph.backend.memory.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WhatsAppImportApiIntegrationTest {

    private static final String IMPORTS = ApiPaths.V1 + "/imports";
    private static final String SEARCH = ApiPaths.V1 + "/search";
    private static final String ASK = ApiPaths.V1 + "/ask";

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void importsAChatIntoDayBucketsThatAreSearchable() throws Exception {
        byte[] chat = new ClassPathResource("fixtures/whatsapp/sample-chat.txt").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "WhatsApp Chat with Rahul.txt",
                "text/plain", chat);

        MvcResult created = mockMvc.perform(multipart(IMPORTS + "/whatsapp")
                        .file(file)
                        .param("zone", "Asia/Kolkata")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.kind").value("WHATSAPP"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        UUID importId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        awaitImportCompleted(importId);

        mockMvc.perform(get(IMPORTS + "/" + importId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.chatName").value("Rahul"))
                .andExpect(jsonPath("$.data.memoriesCreated", greaterThanOrEqualTo(2)));

        mockMvc.perform(get(SEARCH)
                        .param("q", "Sikkim")
                        .param("zone", "Asia/Kolkata")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[0].memory.type").value("CONVERSATION"));

        String memoryId = JsonPath.read(
                mockMvc.perform(get(SEARCH)
                                .param("q", "Sikkim")
                                .param("zone", "Asia/Kolkata")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data.items[0].memory.id");

        mockMvc.perform(get(ApiPaths.V1 + "/memories/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.messages[0].senderName").value("Rahul"))
                .andExpect(jsonPath("$.data.messages[0].body", org.hamcrest.Matchers.containsString("Sikkim")));

        String peopleBody = mockMvc.perform(get(ApiPaths.V1 + "/people")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[?(@.displayName == 'Rahul')].memoryCount").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        java.util.List<String> rahulIds = JsonPath.read(peopleBody, "$.data[?(@.displayName == 'Rahul')].id");
        String personId = rahulIds.get(0);

        mockMvc.perform(get(SEARCH)
                        .param("personId", personId)
                        .param("zone", "Asia/Kolkata")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems", greaterThanOrEqualTo(1)));

        mockMvc.perform(post(ASK)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Sikkim October","zone":"Asia/Kolkata"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grounded").value(true))
                .andExpect(jsonPath("$.data.sources", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void reuploadingTheSameExportIsIdempotent() throws Exception {
        byte[] chat = new ClassPathResource("fixtures/whatsapp/sample-dashed.txt").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "chat.txt", "text/plain", chat);

        String first = mockMvc.perform(multipart(IMPORTS + "/whatsapp")
                        .file(file)
                        .param("zone", "UTC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID firstId = UUID.fromString(JsonPath.read(first, "$.data.id"));
        awaitImportCompleted(firstId);

        MockMultipartFile again = new MockMultipartFile("file", "chat.txt", "text/plain", chat);
        mockMvc.perform(multipart(IMPORTS + "/whatsapp")
                        .file(again)
                        .param("zone", "UTC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(firstId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void neverExposesAnotherUsersImport() throws Exception {
        byte[] chat = "[01/01/2024, 12:00:00] A: hello\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "chat.txt", "text/plain", chat);

        String body = mockMvc.perform(multipart(IMPORTS + "/whatsapp")
                        .file(file)
                        .param("zone", "UTC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID importId = UUID.fromString(JsonPath.read(body, "$.data.id"));

        String other = TestFixtures.registerAndAuthenticate(mockMvc);
        mockMvc.perform(get(IMPORTS + "/" + importId).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private void awaitImportCompleted(UUID importId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        String body = null;
        do {
            body = mockMvc.perform(get(IMPORTS + "/" + importId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String status = JsonPath.read(body, "$.data.status");
            if ("COMPLETED".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("Import failed: " + body);
            }
            Thread.sleep(50);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Import still not completed: " + body);
    }
}
