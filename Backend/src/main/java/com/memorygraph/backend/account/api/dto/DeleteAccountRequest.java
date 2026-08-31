package com.memorygraph.backend.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 32) String confirmation) {
}
