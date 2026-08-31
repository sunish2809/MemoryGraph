package com.memorygraph.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import javax.imageio.ImageIO;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.memorygraph.backend.common.api.ApiPaths;

/** Shared fixtures for tests that need a signed-in account or a genuine image file. */
public final class TestFixtures {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private TestFixtures() {
    }

    /**
     * Waits for enrichment to reach a terminal state and returns the memory as JSON.
     * <p>
     * Polls the real endpoint rather than substituting a synchronous executor, so tests exercise the
     * actual pipeline: the committed transaction, the event, the worker thread and the claim. Enrichment
     * takes tens of milliseconds; the timeout is generous only so a loaded CI machine does not fail the
     * build for being slow.
     */
    public static String awaitProcessed(MockMvc mockMvc, String token, String memoryId) throws Exception {
        Instant deadline = Instant.now().plus(PROCESSING_TIMEOUT);
        String body;

        do {
            body = mockMvc.perform(get(ApiPaths.V1 + "/memories/" + memoryId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String status = JsonPath.read(body, "$.data.processingStatus");
            if (!status.equals("PENDING") && !status.equals("PROCESSING")) {
                return body;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError("Memory " + memoryId + " was still not processed after " + PROCESSING_TIMEOUT
                + ". Last response: " + body);
    }

    /**
     * Waits until a memory has been embedded. Used by search/ask tests that need the vector column
     * populated; hashing embeddings are fast, but the job still goes through the async pipeline.
     */
    public static void awaitEmbedded(org.springframework.jdbc.core.JdbcTemplate jdbc, java.util.UUID memoryId)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(PROCESSING_TIMEOUT);
        do {
            Boolean present = jdbc.queryForObject(
                    "select embedding is not null from memories where id = ?", Boolean.class, memoryId);
            if (Boolean.TRUE.equals(present)) {
                return;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError("Memory " + memoryId + " still had no embedding after " + PROCESSING_TIMEOUT);
    }

    /** Registers a fresh account and returns its bearer token. */
    public static String registerAndAuthenticate(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.V1 + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user-%d@example.com","password":"correct-horse-battery","displayName":"Test Person"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * A real PNG, encoded by the JDK rather than hand-written, so signature detection and dimension
     * reading are tested against a file a camera or phone could plausibly produce.
     */
    public static byte[] pngImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    public static byte[] jpegImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", bytes);
        return bytes.toByteArray();
    }
}
