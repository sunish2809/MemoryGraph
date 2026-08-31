package com.memorygraph.backend.account.api.dto;

/**
 * How this deployment handles the caller's data — shown in Privacy so people can decide whether to
 * import a life archive before they do.
 */
public record PrivacyStatusResponse(
        boolean dataStoredLocally,
        String storageBackend,
        boolean languageModelEnabled,
        String languageModel,
        boolean embeddingsEnabled,
        boolean facesEnabled,
        boolean facesRunLocally) {
}
