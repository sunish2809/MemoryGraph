package com.memorygraph.backend.ai.caption;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when no chat model is configured. Captions are skipped; OCR and filenames still search.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
class NoOpImageCaptionClient implements ImageCaptionClient {

    @Override
    public Optional<String> caption(byte[] imageBytes, String mimeType) {
        return Optional.empty();
    }
}
