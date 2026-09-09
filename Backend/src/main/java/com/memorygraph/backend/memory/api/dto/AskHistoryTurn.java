package com.memorygraph.backend.memory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One earlier question or answer in this Ask thread. */
public record AskHistoryTurn(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 4000) String content) {
}
