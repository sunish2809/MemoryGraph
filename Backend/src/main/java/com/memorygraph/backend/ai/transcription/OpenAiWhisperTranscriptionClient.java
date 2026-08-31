package com.memorygraph.backend.ai.transcription;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Whisper via the audio transcriptions HTTP API. Active when chat is wired to OpenAI
 * (same gate as captions — an API key is expected).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class OpenAiWhisperTranscriptionClient implements TranscriptionClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiWhisperTranscriptionClient(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${memorygraph.ai.transcription-model:whisper-1}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl("https://api.openai.com").build();
    }

    @Override
    public String transcribe(String fileName, String mimeType, InputStream content, long sizeBytes) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new InputStreamResource(content) {
                @Override
                public String getFilename() {
                    return fileName;
                }

                @Override
                public long contentLength() {
                    return sizeBytes;
                }
            }).contentType(MediaType.parseMediaType(mimeType));
            body.part("model", model);
            body.part("response_format", "json");

            JsonNode response = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.hasNonNull("text")) {
                return "";
            }
            String text = response.get("text").asText("").strip();
            log.info("Transcribed {} ({} chars)", fileName, text.length());
            return text;
        } catch (RuntimeException ex) {
            log.warn("Whisper transcription failed for {}: {}", fileName, ex.getMessage());
            return "";
        }
    }
}
