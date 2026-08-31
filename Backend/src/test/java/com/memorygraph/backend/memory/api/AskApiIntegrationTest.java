package com.memorygraph.backend.memory.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

/**
 * Ask end to end, without an OpenAI key: hashing embeddings + retrieval-only answers.
 * <p>
 * The important contracts are that sources are always returned, that another account's memories
 * never appear, and that an empty archive produces an honest "I don't know" rather than a guess.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AskApiIntegrationTest {

    private static final String ASK = ApiPaths.V1 + "/ask";
    private static final String MEMORIES = ApiPaths.V1 + "/memories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void answersFromRetrievedMemoriesAndListsThemAsSources() throws Exception {
        UUID memoryId = createNote("Sikkim trip", "The train to Gangtok was delayed by four hours.",
                "2019-04-02T09:00:00Z");
        TestFixtures.awaitEmbedded(jdbc, memoryId);

        mockMvc.perform(asOwner(post(ASK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What happened on the Sikkim trip?","zone":"UTC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grounded").value(true))
                .andExpect(jsonPath("$.data.model").value("retrieval-only"))
                .andExpect(jsonPath("$.data.answer", containsString("Sikkim trip")))
                .andExpect(jsonPath("$.data.sources", hasSize(1)))
                .andExpect(jsonPath("$.data.sources[0].title").value("Sikkim trip"));
    }

    @Test
    void admitsWhenNothingRelevantWasFound() throws Exception {
        UUID memoryId = createNote("Tax return", "Filed the paperwork.", "2019-07-11T09:00:00Z");
        TestFixtures.awaitEmbedded(jdbc, memoryId);

        mockMvc.perform(asOwner(post(ASK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Tell me about my scuba diving holiday"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grounded").value(false))
                .andExpect(jsonPath("$.data.sources").isEmpty())
                .andExpect(jsonPath("$.data.answer", containsString("could not find")));
    }

    @Test
    void neverUsesAnotherAccountsMemories() throws Exception {
        UUID memoryId = createNote("Sikkim trip", "The train to Gangtok was delayed.", "2019-04-02T09:00:00Z");
        TestFixtures.awaitEmbedded(jdbc, memoryId);

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(post(ASK)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Sikkim trip"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grounded").value(false))
                .andExpect(jsonPath("$.data.sources").isEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(ASK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"anything"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        mockMvc.perform(asOwner(post(ASK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private UUID createNote(String title, String content, String occurredAt) throws Exception {
        String body = mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s","occurredAt":"%s"}
                                """.formatted(title, content, occurredAt)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
