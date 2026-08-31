package com.memorygraph.backend.memory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.memory.domain.FaceDetection;
import com.memorygraph.backend.memory.domain.FaceDetectionRepository;
import com.memorygraph.backend.support.TestFixtures;
import com.memorygraph.backend.support.TestcontainersConfiguration;

/**
 * The memory contract end to end: creating notes, uploading photos, reading them back, and — most
 * importantly — the guarantee that one account can never reach another's memories or media.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemoryApiIntegrationTest {

    private static final String MEMORIES = ApiPaths.V1 + "/memories";

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
    void createsATextMemoryThatIsImmediatelyComplete() throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"First light","content":"Woke up before sunrise in Ladakh.",
                                 "occurredAt":"2024-06-01T00:35:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.title").value("First light"))
                .andExpect(jsonPath("$.data.content").value("Woke up before sunrise in Ladakh."))
                .andExpect(jsonPath("$.data.occurredAt").value("2024-06-01T00:35:00Z"))
                // Nothing to extract from typed text, so it never sits in PENDING.
                .andExpect(jsonPath("$.data.processingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.assets").isEmpty());
    }

    @Test
    void defaultsAnUnspecifiedTimeToNow() throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"A quick thought."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.occurredAt").isNotEmpty());
    }

    @Test
    void rejectsATextMemoryWithNoContent() throws Exception {
        mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Empty","content":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.content").exists());
    }

    /**
     * The upload responds before enrichment has run, which is the point of doing it asynchronously: a
     * user waits for the file to be safe, not for it to be analysed.
     */
    @Test
    void respondsToAnUploadBeforeEnrichmentHasRun() throws Exception {
        MvcResult upload = uploadPhoto("pending.png", 64, 64);

        String body = upload.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.data.processingStatus")).isEqualTo("PENDING");
        assertThat((String) JsonPath.read(body, "$.data.assets[0].fileName")).isEqualTo("pending.png");
    }

    @Test
    void uploadsAPhotoAndEnrichesItWithItsDimensions() throws Exception {
        MvcResult upload = uploadPhoto("sikkim-day2.png", 320, 240);
        String memoryId = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.id");

        String processed = TestFixtures.awaitProcessed(mockMvc, token, memoryId);

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PHOTO"))
                .andExpect(jsonPath("$.data.source").value("UPLOAD"))
                .andExpect(jsonPath("$.data.processingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.assets[0].fileName").value("sikkim-day2.png"))
                .andExpect(jsonPath("$.data.assets[0].mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.assets[0].widthPx").value(320))
                .andExpect(jsonPath("$.data.assets[0].heightPx").value(240))
                .andExpect(jsonPath("$.data.assets[0].sizeBytes", greaterThan(0)))
                // The filename is folded into searchable text: often the only words a photo has.
                .andExpect(jsonPath("$.data.content").value("A ridge above the clouds\nsikkim-day2.png"));

        assertThat((String) JsonPath.read(processed, "$.data.processingStatus")).isEqualTo("COMPLETED");
    }

    @Test
    void servesTheStoredBytesBackToTheirOwner() throws Exception {
        MvcResult upload = uploadPhoto("view.png", 32, 32);
        String body = upload.getResponse().getContentAsString();

        String downloadPath = JsonPath.read(body, "$.data.assets[0].downloadPath");

        mockMvc.perform(asOwner(get(downloadPath)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                // A private photo must never be held by a shared cache.
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")));
    }

    @Test
    void rejectsAnUploadThatIsNotAnImage() throws Exception {
        MockMultipartFile notAnImage = new MockMultipartFile("file", "notes.txt", "text/plain",
                "just some text".getBytes());

        mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(notAnImage)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsAnUploadLargerThanTheConfiguredLimit() throws Exception {
        // The test profile caps uploads at 2MB.
        MockMultipartFile oversized = new MockMultipartFile("file", "huge.png", "image/png", new byte[3 * 1024 * 1024]);

        mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(oversized)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void listsMemoriesNewestFirst() throws Exception {
        createTextMemory("Oldest", "2020-01-01T12:00:00Z");
        createTextMemory("Middle", "2022-01-01T12:00:00Z");
        createTextMemory("Newest", "2024-01-01T12:00:00Z");

        mockMvc.perform(asOwner(get(MEMORIES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("Newest"))
                .andExpect(jsonPath("$.data.items[2].title").value("Oldest"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void paginatesTheList() throws Exception {
        createTextMemory("One", "2021-01-01T12:00:00Z");
        createTextMemory("Two", "2022-01-01T12:00:00Z");
        createTextMemory("Three", "2023-01-01T12:00:00Z");

        mockMvc.perform(asOwner(get(MEMORIES)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void deletesAMemoryAndItsMedia() throws Exception {
        MvcResult upload = uploadPhoto("gone.png", 16, 16);
        String body = upload.getResponse().getContentAsString();
        String memoryId = JsonPath.read(body, "$.data.id");
        String downloadPath = JsonPath.read(body, "$.data.assets[0].downloadPath");
        TestFixtures.awaitProcessed(mockMvc, token, memoryId);

        mockMvc.perform(asOwner(delete(MEMORIES + "/" + memoryId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asOwner(get(downloadPath)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneAccountCannotReadAnothersMemory() throws Exception {
        String memoryId = JsonPath.read(createTextMemory("Private", "2023-05-05T10:00:00Z"), "$.data.id");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(MEMORIES + "/" + memoryId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void oneAccountCannotDownloadAnothersMedia() throws Exception {
        MvcResult upload = uploadPhoto("private.png", 16, 16);
        String downloadPath = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.assets[0].downloadPath");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(downloadPath).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneAccountCannotDeleteAnothersMemory() throws Exception {
        String memoryId = JsonPath.read(createTextMemory("Mine", "2023-05-05T10:00:00Z"), "$.data.id");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(delete(MEMORIES + "/" + memoryId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isOk());
    }

    @Test
    void aListOnlyContainsTheCallersOwnMemories() throws Exception {
        createTextMemory("Mine", "2023-01-01T12:00:00Z");

        String otherToken = TestFixtures.registerAndAuthenticate(mockMvc);

        mockMvc.perform(get(MEMORIES).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void memoryEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(MEMORIES)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(MEMORIES + "/text")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(MEMORIES + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanCorrectTitleCaptionAndDate() throws Exception {
        String memoryId = JsonPath.read(createTextMemory("Wrong title", "2020-01-01T12:00:00Z"), "$.data.id");

        mockMvc.perform(asOwner(patch(MEMORIES + "/" + memoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Right title","content":"Corrected note.","occurredAt":"2021-06-15T08:30:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Right title"))
                .andExpect(jsonPath("$.data.content").value("Corrected note."))
                .andExpect(jsonPath("$.data.occurredAt").value("2021-06-15T08:30:00Z"));
    }

    @Test
    void clearingAFaceNameLetsYouConfirmAgain() throws Exception {
        MvcResult upload = uploadPhoto("faces.png", 64, 64);
        String memoryId = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.id");
        String assetId = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.assets[0].id");
        UUID memoryUuid = UUID.fromString(memoryId);
        UUID userId = jdbc.queryForObject("select user_id from memories where id = ?", UUID.class, memoryUuid);
        TestFixtures.awaitProcessed(mockMvc, token, memoryId);

        FaceDetection face = FaceDetection.create(memoryUuid, userId, UUID.fromString(assetId), 0.1, 0.1, 0.2, 0.2);
        faces.save(face);
        String faceId = face.getId().toString();

        mockMvc.perform(asOwner(post(MEMORIES + "/" + memoryId + "/faces/" + faceId + "/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Raj"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personName").value("Raj"));

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people[0].displayName").value("Raj"));

        mockMvc.perform(asOwner(delete(MEMORIES + "/" + memoryId + "/faces/" + faceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personId").doesNotExist())
                .andExpect(jsonPath("$.data.suggestedPersonName").value("Raj"));

        mockMvc.perform(asOwner(get(MEMORIES + "/" + memoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.people").isEmpty());

        mockMvc.perform(asOwner(post(MEMORIES + "/" + memoryId + "/faces/" + faceId + "/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Raj Sharma"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personName").value("Raj Sharma"));
    }

    private String createTextMemory(String title, String occurredAt) throws Exception {
        return mockMvc.perform(asOwner(post(MEMORIES + "/text"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"Content of %s","occurredAt":"%s"}
                                """.formatted(title, title, occurredAt)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private MvcResult uploadPhoto(String fileName, int width, int height) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "image/png",
                TestFixtures.pngImage(width, height));

        return mockMvc.perform(asOwner(multipart(MEMORIES + "/upload").file(file))
                        .param("title", "A ridge above the clouds"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T asOwner(T builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
