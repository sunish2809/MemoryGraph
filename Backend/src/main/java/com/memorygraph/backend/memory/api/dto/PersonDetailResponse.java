package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Person;

/**
 * A person plus connected-memory breakdown (the personal memory graph view of one node).
 * {@code photos} is the gallery; {@code memories} is recent non-photo items (chats, notes).
 */
public record PersonDetailResponse(
        UUID id,
        String displayName,
        long memoryCount,
        Instant createdAt,
        ConnectedCounts connected,
        List<MemorySummaryResponse> memories,
        List<MemorySummaryResponse> photos) {

    public record ConnectedCounts(
            long conversations,
            long photos,
            long videos,
            long audio,
            long documents,
            long text,
            long events,
            long places) {
    }

    public static PersonDetailResponse from(
            Person person,
            long memoryCount,
            ConnectedCounts connected,
            List<MemorySummaryResponse> memories,
            List<MemorySummaryResponse> photos) {
        return new PersonDetailResponse(
                person.getId(),
                person.getDisplayName(),
                memoryCount,
                person.getCreatedAt(),
                connected,
                memories,
                photos);
    }
}
