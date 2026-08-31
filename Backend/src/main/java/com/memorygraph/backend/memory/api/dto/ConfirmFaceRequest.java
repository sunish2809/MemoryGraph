package com.memorygraph.backend.memory.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Confirm or assign a face detection to a person. Provide either an existing {@code personId} or a
 * new {@code displayName}.
 */
public record ConfirmFaceRequest(
        UUID personId,
        @Size(min = 1, max = 255) String displayName) {

    public void requireIdentity() {
        boolean hasPerson = personId != null;
        boolean hasName = displayName != null && !displayName.isBlank();
        if (hasPerson == hasName) {
            throw new IllegalArgumentException("Provide exactly one of personId or displayName");
        }
    }
}
