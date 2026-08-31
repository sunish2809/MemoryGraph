package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.Size;

/**
 * Partial update of a memory. Null fields are left unchanged; blank title/description/content clears
 * them. {@code occurredAt} is a user correction and locks the date against later EXIF overwrites.
 */
public record UpdateMemoryRequest(
        @Size(max = 255) String title,
        @Size(max = 10_000) String description,
        @Size(max = 100_000) String content,
        Instant occurredAt) {
}
