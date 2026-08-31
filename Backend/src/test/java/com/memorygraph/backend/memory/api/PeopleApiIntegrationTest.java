package com.memorygraph.backend.memory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class PeopleApiIntegrationTest {

    private static final String MEMORIES = ApiPaths.V1 + "/memories";
    private static final String PEOPLE = ApiPaths.V1 + "/people";

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void renameUpdatesTheDisplayNameInOnePlace() throws Exception {
        String memoryId = createTextMemory("Chat", "2023-01-01T12:00:00Z");
        String personId = tag(memoryId, "raj");

        mockMvc.perform(asOwner(patch(PEOPLE + "/" + personId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Raj Sharma"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Raj Sharma"));

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people[0].displayName").value("Raj Sharma"));
    }

    @Test
    void renameToAnExistingPersonSuggestsMerge() throws Exception {
        String first = createTextMemory("One", "2023-01-01T12:00:00Z");
        String second = createTextMemory("Two", "2023-02-01T12:00:00Z");
        String rajId = tag(first, "Raj");
        tag(second, "Raj Sharma");

        mockMvc.perform(asOwner(patch(PEOPLE + "/" + rajId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Raj Sharma"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NAME_ALREADY_TAKEN"));
    }

    @Test
    void mergeMovesLinksAndDeletesTheDuplicate() throws Exception {
        String first = createTextMemory("One", "2023-01-01T12:00:00Z");
        String second = createTextMemory("Two", "2023-02-01T12:00:00Z");
        String keepId = tag(first, "Raj");
        String sourceId = tag(second, "Raj Sharma");

        mockMvc.perform(asOwner(post(PEOPLE + "/" + keepId + "/merge"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourcePersonId":"%s"}
                                """.formatted(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(keepId))
                .andExpect(jsonPath("$.data.displayName").value("Raj"))
                .andExpect(jsonPath("$.data.memoryCount").value(2));

        mockMvc.perform(asOwner(get(PEOPLE + "/" + sourceId))).andExpect(status().isNotFound());

        mockMvc.perform(asOwner(get(PEOPLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].displayName").value("Raj"));
    }

    @Test
    void personPageIncludesAPhotoGallery() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "raj.png", "image/png",
                TestFixtures.pngImage(32, 32));
        String memoryId = JsonPath.read(mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file))
                        .param("title", "Raj at the ridge"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        String personId = tag(memoryId, "Raj");

        mockMvc.perform(asOwner(get(PEOPLE + "/" + personId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photos.length()").value(1))
                .andExpect(jsonPath("$.data.photos[0].id").value(memoryId))
                .andExpect(jsonPath("$.data.photos[0].title").value("Raj at the ridge"))
                .andExpect(jsonPath("$.data.memories").isEmpty());
    }

    @Test
    void oneAccountCannotRenameAnothersPerson() throws Exception {
        String memoryId = createTextMemory("Mine", "2023-01-01T12:00:00Z");
        String personId = tag(memoryId, "Raj");
        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(patch(PEOPLE + "/" + personId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Someone else"}
                                """))
                .andExpect(status().isNotFound());
    }

    private String tag(String memoryId, String displayName) throws Exception {
        String body = mockMvc.perform(asOwner(post(MEMORIES + "/" + memoryId + "/people"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s"}
                                """.formatted(displayName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createTextMemory(String title, String occurredAt) throws Exception {
        return JsonPath.read(mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"Content of %s","occurredAt":"%s"}
                                """.formatted(title, title, occurredAt)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
