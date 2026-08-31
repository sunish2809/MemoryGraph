package com.memorygraph.backend.memory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.memorygraph.backend.memory.domain.FaceDetection;
import com.memorygraph.backend.memory.domain.FaceDetectionRepository;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FacesApiIntegrationTest {

    private static final String MEMORIES = ApiPaths.V1 + "/memories";
    private static final String FACES = ApiPaths.V1 + "/faces";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FaceDetectionRepository faces;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = TestFixtures.registerAndAuthenticate(mockMvc);
    }

    @Test
    void reviewQueueListsUnlabeledFaces() throws Exception {
        FaceSeed seed = insertUnnamedFace();

        mockMvc.perform(asOwner(get(FACES + "/review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlabeledCount").value(1))
                .andExpect(jsonPath("$.data.groups[0].faces[0].id").value(seed.faceId().toString()))
                .andExpect(jsonPath("$.data.groups[0].faces[0].memoryId").value(seed.memoryId().toString()));
    }

    @Test
    void rejectingASuggestionClearsLooksLikeWithoutNaming() throws Exception {
        FaceSeed seed = insertUnnamedFace();
        String personId = JsonPath.read(mockMvc.perform(asOwner(post(MEMORIES + "/" + seed.memoryId() + "/people"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Raj"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        FaceDetection face = faces.findById(seed.faceId()).orElseThrow();
        face.suggest(UUID.fromString(personId), 0.88);
        faces.save(face);

        mockMvc.perform(asOwner(post(FACES + "/" + seed.faceId() + "/reject-suggestion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestedPersonId").doesNotExist())
                .andExpect(jsonPath("$.data.personId").doesNotExist());

        mockMvc.perform(asOwner(get(MEMORIES + "/" + seed.memoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faces[0].suggestedPersonName").doesNotExist());
    }

    @Test
    void ignoringAFaceRemovesItFromTheReviewQueue() throws Exception {
        FaceSeed seed = insertUnnamedFace();

        mockMvc.perform(asOwner(post(FACES + "/" + seed.faceId() + "/ignore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ignored").value(true))
                .andExpect(jsonPath("$.data.personId").doesNotExist());

        mockMvc.perform(asOwner(get(FACES + "/review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlabeledCount").value(0));

        mockMvc.perform(asOwner(get(MEMORIES + "/" + seed.memoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faces[0].ignored").value(true));

        mockMvc.perform(asOwner(post(FACES + "/" + seed.faceId() + "/restore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ignored").value(false));

        mockMvc.perform(asOwner(get(FACES + "/review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlabeledCount").value(1));
    }

    @Test
    void ignoringAClusterSkipsEveryUnlabeledMember() throws Exception {
        FaceSeed first = insertUnnamedFace();
        FaceSeed second = insertUnnamedFace();
        UUID clusterId = UUID.randomUUID();
        FaceDetection a = faces.findById(first.faceId()).orElseThrow();
        FaceDetection b = faces.findById(second.faceId()).orElseThrow();
        a.assignCluster(clusterId);
        b.assignCluster(clusterId);
        faces.save(a);
        faces.save(b);

        mockMvc.perform(asOwner(post(FACES + "/clusters/" + clusterId + "/ignore")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlabeledCount").value(0));
    }

    @Test
    void namingAClusterAssignsEveryUnlabeledMember() throws Exception {
        FaceSeed first = insertUnnamedFace();
        FaceSeed second = insertUnnamedFace();
        UUID clusterId = UUID.randomUUID();
        FaceDetection a = faces.findById(first.faceId()).orElseThrow();
        FaceDetection b = faces.findById(second.faceId()).orElseThrow();
        a.assignCluster(clusterId);
        b.assignCluster(clusterId);
        faces.save(a);
        faces.save(b);

        mockMvc.perform(asOwner(post(FACES + "/clusters/" + clusterId + "/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Aditya"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlabeledCount").value(0));

        mockMvc.perform(asOwner(get(MEMORIES + "/" + first.memoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people[0].displayName").value("Aditya"));
    }

    private FaceSeed insertUnnamedFace() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "face.png", "image/png",
                TestFixtures.pngImage(32, 32));
        String body = mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file))
                        .param("title", "A face"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID memoryId = UUID.fromString(JsonPath.read(body, "$.data.id"));
        UUID assetId = UUID.fromString(JsonPath.read(body, "$.data.assets[0].id"));
        TestFixtures.awaitProcessed(mockMvc, token, memoryId.toString());
        UUID userId = jdbc.queryForObject("select user_id from memories where id = ?", UUID.class, memoryId);
        FaceDetection face = FaceDetection.create(memoryId, userId, assetId, 0.2, 0.2, 0.3, 0.3);
        faces.save(face);
        return new FaceSeed(memoryId, face.getId());
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private record FaceSeed(UUID memoryId, UUID faceId) {
    }
}
