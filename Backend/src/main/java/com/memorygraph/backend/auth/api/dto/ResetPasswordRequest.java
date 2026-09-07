package com.memorygraph.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 20, max = 128) String token,

        @NotBlank @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        String password) {
}
