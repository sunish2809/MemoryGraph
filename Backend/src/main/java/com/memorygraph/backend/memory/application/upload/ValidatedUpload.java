package com.memorygraph.backend.memory.application.upload;

/**
 * An upload that has passed validation: the type is one we support and was confirmed from the file's
 * own bytes, and the display name is safe to store and render.
 */
public record ValidatedUpload(SupportedMediaType mediaType, String fileName, long sizeBytes) {
}
