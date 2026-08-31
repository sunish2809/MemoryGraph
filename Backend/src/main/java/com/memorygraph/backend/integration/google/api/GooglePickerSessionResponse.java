package com.memorygraph.backend.integration.google.api;

public record GooglePickerSessionResponse(
        String sessionId,
        String pickerUri,
        long pollIntervalMs,
        boolean mediaItemsSet) {
}
