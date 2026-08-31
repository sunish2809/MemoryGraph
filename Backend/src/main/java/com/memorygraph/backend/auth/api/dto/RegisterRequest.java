package com.memorygraph.backend.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,

        // Length is the only rule enforced for now; complexity rules belong in a dedicated policy
        // if and when the product needs them.
        @NotBlank @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        String password,

        @NotBlank @Size(min = 1, max = 120) String displayName,

        /** Required when the server is running a closed beta ({@code REGISTRATION_INVITE_CODE}). */
        @Size(max = 128) String inviteCode) {
}
