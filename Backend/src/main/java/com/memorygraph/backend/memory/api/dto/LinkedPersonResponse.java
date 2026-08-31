package com.memorygraph.backend.memory.api.dto;

import java.util.UUID;

import com.memorygraph.backend.memory.domain.Person;

/** Compact person chip for memory detail and face confirm UI. */
public record LinkedPersonResponse(UUID id, String displayName) {

    public static LinkedPersonResponse from(Person person) {
        return new LinkedPersonResponse(person.getId(), person.getDisplayName());
    }
}
