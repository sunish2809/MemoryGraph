package com.memorygraph.backend.memory.application.processing;

import java.util.UUID;

/**
 * Published after a memory and its processing job are committed. Carrying only the id keeps the
 * asynchronous listener from touching an entity attached to a transaction that has already closed.
 */
public record ProcessingJobQueued(UUID jobId) {
}
