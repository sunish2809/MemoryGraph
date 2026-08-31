package com.memorygraph.backend.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmbeddingPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemoryEmbeddingStore store;

    @Test
    void embedsATextMemoryThroughTheAsyncPipeline() throws Exception {
        String token = TestFixtures.registerAndAuthenticate(mockMvc);

        String body = mockMvc.perform(post(ApiPaths.V1 + "/memories/text")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sikkim","content":"Planned the trip over coffee.","occurredAt":"2019-01-01T09:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID memoryId = UUID.fromString(JsonPath.read(body, "$.data.id"));
        TestFixtures.awaitEmbedded(jdbc, memoryId);

        assertThat(store.exists(memoryId)).isTrue();
    }
}
