package com.memorygraph.backend.memory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.memory.application.PlaceLinkService;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TripsApiIntegrationTest {

    private static final String MEMORIES = ApiPaths.V1 + "/memories";
    private static final String TRIPS = ApiPaths.V1 + "/trips";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceLinkService placeLinks;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void gpsStretchAcrossTwoPlacesIsSuggestedThenSaved() throws Exception {
        linkPhoto("Ridge", "2019-10-12T08:00:00Z", 27.33, 88.61);
        linkPhoto("Market", "2019-10-13T09:00:00Z", 27.04, 88.26);

        mockMvc.perform(asOwner(get(TRIPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(0))
                .andExpect(jsonPath("$.data.suggestions.length()").value(1))
                .andExpect(jsonPath("$.data.suggestions[0].placeCount").value(2))
                .andExpect(jsonPath("$.data.suggestions[0].memoryCount").value(2));

        String startedAt = "2019-10-12T08:00:00Z";
        String endedAt = "2019-10-13T09:00:00Z";
        String body = mockMvc.perform(asOwner(post(TRIPS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sikkim","startedAt":"%s","endedAt":"%s"}
                                """.formatted(startedAt, endedAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Sikkim"))
                .andExpect(jsonPath("$.data.memoryCount").value(2))
                .andExpect(jsonPath("$.data.places.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tripId = JsonPath.read(body, "$.data.id");

        mockMvc.perform(asOwner(get(TRIPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(1))
                .andExpect(jsonPath("$.data.suggestions.length()").value(0));

        mockMvc.perform(asOwner(delete(TRIPS + "/" + tripId))).andExpect(status().isNoContent());
        mockMvc.perform(asOwner(get(TRIPS + "/" + tripId))).andExpect(status().isNotFound());
    }

    @Test
    void aTripCannotEndBeforeItStarts() throws Exception {
        mockMvc.perform(asOwner(post(TRIPS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backwards","startedAt":"2019-10-13T00:00:00Z","endedAt":"2019-10-12T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private void linkPhoto(String title, String occurredAt, double latitude, double longitude) throws Exception {
        String memoryId = JsonPath.read(mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s","occurredAt":"%s"}
                                """.formatted(title, title, occurredAt)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        UUID userId = jdbc.queryForObject("select user_id from memories where id = ?", UUID.class,
                UUID.fromString(memoryId));
        placeLinks.upsertAndLink(userId, UUID.fromString(memoryId), latitude, longitude);
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
