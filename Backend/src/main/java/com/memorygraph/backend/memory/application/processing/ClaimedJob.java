package com.memorygraph.backend.memory.application.processing;

import java.util.UUID;

import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

/**
 * A job this instance has taken ownership of. Carries the identifiers the observability requirement
 * asks for, so running the job needs no further reads just to produce a useful log line.
 */
public record ClaimedJob(UUID jobId, UUID memoryId, UUID userId, ProcessingJobType type, int attempt) {

    static ClaimedJob from(ProcessingJob job) {
        return new ClaimedJob(job.getId(), job.getMemoryId(), job.getUserId(), job.getType(), job.getAttempts());
    }
}
