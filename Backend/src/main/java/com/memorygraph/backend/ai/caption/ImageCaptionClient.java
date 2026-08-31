package com.memorygraph.backend.ai.caption;

/**
 * Produces a short natural-language description of a photo for searchable content.
 */
public interface ImageCaptionClient {

    /**
     * @return a caption, or empty when the provider cannot or should not caption this image
     */
    java.util.Optional<String> caption(byte[] imageBytes, String mimeType);
}
