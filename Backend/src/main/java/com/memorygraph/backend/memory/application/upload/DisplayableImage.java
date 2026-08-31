package com.memorygraph.backend.memory.application.upload;

/**
 * Image bytes ready to store and show in a browser. HEIC is converted to JPEG before this is built.
 */
public record DisplayableImage(byte[] bytes, String fileName, SupportedMediaType mediaType) {
}
