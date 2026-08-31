package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Person;

/** One person in a list, with how many memories they appear in. */
public record PersonSummaryResponse(
        UUID id,
        String displayName,
        long memoryCount,
        Instant createdAt) {

    public static PersonSummaryResponse from(Person person, long memoryCount) {
        return new PersonSummaryResponse(person.getId(), person.getDisplayName(), memoryCount,
                person.getCreatedAt());
    }
}
