package com.memorygraph.backend.memory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.memory.application.PlaceLinkService;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlacesApiIntegrationTest {

    private static final String MEMORIES = ApiPaths.V1 + "/memories";
    private static final String PLACES = ApiPaths.V1 + "/places";

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
    void renameLocksTheDisplayName() throws Exception {
        PlaceSeed seed = insertPlace(27.33, 88.61);

        mockMvc.perform(asOwner(patch(PLACES + "/" + seed.placeId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Gangtok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Gangtok"))
                .andExpect(jsonPath("$.data.nameLocked").value(true));
    }

    @Test
    void mergeMovesLinksAndKeepsTheSourceGpsCellAsAnAlias() throws Exception {
        PlaceSeed keep = insertPlace(27.33, 88.61);
        PlaceSeed source = insertPlace(27.38, 88.70);
        mockMvc.perform(asOwner(patch(PLACES + "/" + keep.placeId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Gangtok"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(asOwner(post(PLACES + "/" + keep.placeId() + "/merge"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourcePlaceId":"%s"}
                                """.formatted(source.placeId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(keep.placeId().toString()))
                .andExpect(jsonPath("$.data.memoryCount").value(2));

        mockMvc.perform(asOwner(get(PLACES + "/" + source.placeId()))).andExpect(status().isNotFound());

        UUID userId = jdbc.queryForObject("select user_id from memories where id = ?", UUID.class, keep.memoryId());
        Place resolved = placeLinks.upsertAndLink(userId, keep.memoryId(), 27.38, 88.70);
        org.assertj.core.api.Assertions.assertThat(resolved.getId()).isEqualTo(keep.placeId());
    }

    private PlaceSeed insertPlace(double latitude, double longitude) throws Exception {
        String memoryId = JsonPath.read(mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"At %.2f","content":"GPS memory","occurredAt":"2019-10-12T08:00:00Z"}
                                """.formatted(latitude)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        UUID userId = jdbc.queryForObject("select user_id from memories where id = ?", UUID.class,
                UUID.fromString(memoryId));
        Place place = placeLinks.upsertAndLink(userId, UUID.fromString(memoryId), latitude, longitude);
        return new PlaceSeed(UUID.fromString(memoryId), place.getId());
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private record PlaceSeed(UUID memoryId, UUID placeId) {
    }
}
