package com.memorygraph.backend.memory.application.imports;

import java.util.UUID;

/** Fired after an {@link com.memorygraph.backend.memory.domain.ImportJob} is committed as PENDING. */
public record ImportJobQueued(UUID jobId) {
}
