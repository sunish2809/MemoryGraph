package com.memorygraph.backend.memory.application.face;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.memorygraph.backend.common.config.FacesProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "memorygraph.faces", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpFaceDetectionClient implements FaceDetectionClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient http;
    private final FacesProperties properties;

    public HttpFaceDetectionClient(FacesProperties properties) {
        this.properties = properties;
        this.http = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.serviceUrl()))
                .build();
    }

    @Override
    public boolean isAvailable() {
        if (!properties.enabled()) {
            return false;
        }
        try {
            String body = http.get().uri("/health").retrieve().body(String.class);
            return body != null && body.contains("ok");
        } catch (RestClientException ex) {
            log.debug("Face service health check failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<DetectedFace> detect(byte[] imageBytes, String mimeType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "photo.jpg";
            }
        }).contentType(MediaType.parseMediaType(
                mimeType != null && !mimeType.isBlank() ? mimeType : "image/jpeg"));

        try {
            String json = http.post()
                    .uri("/detect")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(String.class);
            return parse(json);
        } catch (RestClientException ex) {
            log.warn("Face detection request failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static List<DetectedFace> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = MAPPER.readTree(json);
        JsonNode faces = root.path("faces");
        if (!faces.isArray()) {
            return List.of();
        }
        List<DetectedFace> result = new ArrayList<>();
        for (JsonNode face : faces) {
            JsonNode bbox = face.path("bbox");
            JsonNode emb = face.path("embedding");
            if (!bbox.isArray() || bbox.size() < 4 || !emb.isArray()) {
                continue;
            }
            float[] embedding = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                embedding[i] = (float) emb.get(i).asDouble();
            }
            result.add(new DetectedFace(
                    bbox.get(0).asDouble(),
                    bbox.get(1).asDouble(),
                    bbox.get(2).asDouble(),
                    bbox.get(3).asDouble(),
                    embedding));
        }
        return result;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
