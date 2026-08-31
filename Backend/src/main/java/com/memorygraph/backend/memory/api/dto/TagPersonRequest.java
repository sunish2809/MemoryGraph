package com.memorygraph.backend.memory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tag a person onto a memory by display name (creates the person if needed). */
public record TagPersonRequest(
        @NotBlank @Size(min = 1, max = 255) String displayName) {
}
