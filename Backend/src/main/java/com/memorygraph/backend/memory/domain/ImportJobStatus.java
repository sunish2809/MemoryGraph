package com.memorygraph.backend.memory.domain;

/** Lifecycle of a bulk import run. */
public enum ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
