package com.memorygraph.backend.ai.transcription;

import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Used when no OpenAI chat model is configured. Audio/video still upload and embed on filename text.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
public class NoOpTranscriptionClient implements TranscriptionClient {

    @Override
    public String transcribe(String fileName, String mimeType, InputStream content, long sizeBytes) {
        log.debug("Transcription skipped (no chat/OpenAI provider): {}", fileName);
        return "";
    }
}
