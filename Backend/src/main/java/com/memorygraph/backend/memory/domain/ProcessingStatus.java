package com.memorygraph.backend.memory.domain;

/**
 * Lifecycle of the enrichment pipeline (metadata extraction, OCR, transcription, embeddings) that
 * turns a raw import into a searchable memory.
 */
public enum ProcessingStatus {

    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,

    /** Nothing to enrich, for example a plain text memory before embeddings exist. */
    SKIPPED
}
