package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.ProcessingStatus;

/** One memory in a list. Carries an excerpt rather than full text, so listing 50 notes stays cheap. */
public record MemorySummaryResponse(
        UUID id,
        MemoryType type,
        MemorySource source,
        String title,
        String excerpt,
        Instant occurredAt,
        ProcessingStatus processingStatus,
        List<MediaAssetResponse> assets) {

    private static final int EXCERPT_LENGTH = 280;

    public static MemorySummaryResponse from(Memory memory) {
        return new MemorySummaryResponse(
                memory.getId(),
                memory.getType(),
                memory.getSource(),
                memory.getTitle(),
                excerpt(memory.getContent()),
                memory.getOccurredAt(),
                memory.getProcessingStatus(),
                memory.getAssets().stream().map(asset -> MediaAssetResponse.from(asset, memory.getId())).toList());
    }

    private static String excerpt(String content) {
        if (content == null || content.length() <= EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }
}
