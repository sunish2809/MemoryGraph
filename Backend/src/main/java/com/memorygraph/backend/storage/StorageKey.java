package com.memorygraph.backend.storage;

import java.util.Locale;
import java.util.UUID;

import org.springframework.util.StringUtils;

/**
 * Location of one binary object in the store.
 * <p>
 * The layout is owned here rather than by callers so that every object is namespaced by user, which
 * makes it possible to delete or export one person's data by prefix. Keys are always generated: a
 * client-supplied filename never reaches the filesystem, which removes path traversal as a concern.
 */
public record StorageKey(String value) {

    private static final int MAX_EXTENSION_LENGTH = 12;

    public StorageKey {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        if (value.contains("..") || value.startsWith("/")) {
            throw new IllegalArgumentException("Storage key must be a relative path without traversal: " + value);
        }
    }

    /**
     * Builds a key of the form {@code users/{userId}/memories/{memoryId}/{random}.{ext}}. The
     * extension is derived from the original filename purely so downloaded files open in the right
     * application; it is sanitised and never trusted.
     */
    public static StorageKey forMemoryAsset(UUID userId, UUID memoryId, String originalFileName) {
        String extension = safeExtension(originalFileName);
        String name = UUID.randomUUID() + extension;
        return new StorageKey("users/%s/memories/%s/%s".formatted(userId, memoryId, name));
    }

    /** Export payload for a bulk import, namespaced so one person's imports can be wiped by prefix. */
    public static StorageKey forImport(UUID userId, UUID importId, String originalFileName) {
        String extension = safeExtension(originalFileName);
        String name = UUID.randomUUID() + extension;
        return new StorageKey("users/%s/imports/%s/%s".formatted(userId, importId, name));
    }

    public static StorageKey userPrefix(UUID userId) {
        return new StorageKey("users/" + userId);
    }

    private static String safeExtension(String originalFileName) {
        String extension = StringUtils.getFilenameExtension(originalFileName);
        if (extension == null) {
            return "";
        }
        String cleaned = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (cleaned.isEmpty() || cleaned.length() > MAX_EXTENSION_LENGTH) {
            return "";
        }
        return "." + cleaned;
    }

    @Override
    public String toString() {
        return value;
    }
}
