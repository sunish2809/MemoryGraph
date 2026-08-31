package com.memorygraph.backend.memory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenamePlaceRequest(@NotBlank @Size(max = 255) String displayName) {
}
