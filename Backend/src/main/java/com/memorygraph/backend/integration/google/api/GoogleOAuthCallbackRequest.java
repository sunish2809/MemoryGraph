package com.memorygraph.backend.integration.google.api;

import jakarta.validation.constraints.NotBlank;

public record GoogleOAuthCallbackRequest(
        @NotBlank String code,
        @NotBlank String state) {
}
