package com.memorygraph.backend.integration.google.api;

import jakarta.validation.constraints.NotBlank;

public record GooglePickerImportRequest(@NotBlank String zone) {
}
