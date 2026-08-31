package com.memorygraph.backend.memory.application.processing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns every state change of a {@link ProcessingJob}, each in its own short transaction.
 * <p>
 * Claiming is separated from doing the work on purpose: the claim commits immediately, so a job's
 * {@code PROCESSING} state is visible to operators and to other workers while the work runs, and no
 * database row stays locked for the duration of the work itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingJobTransitions {

    private final ProcessingJobRepository jobs;
    private final ProcessingProperties properties;

    /**
     * Takes ownership of a specific job. Empty when someone else already has it, which is the normal
     * outcome when the sweeper and the post-upload dispatch race for the same job.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedJob> claim(UUID jobId) {
        Optional<ProcessingJob> found = jobs.findByIdForUpdate(jobId);
        if (found.isEmpty()) {
            log.warn("Processing job {} no longer exists", jobId);
            return Optional.empty();
        }

        ProcessingJob job = found.get();
        boolean alreadyTaken = job.getStatus() == ProcessingStatus.PROCESSING
                || job.getStatus() == ProcessingStatus.COMPLETED;
        if (alreadyTaken || job.hasExhaustedAttempts()) {
            return Optional.empty();
        }

        job.markStarted();
        return Optional.of(ClaimedJob.from(job));
    }

    /** Claims everything currently due, and returns the jobs now owned by this instance. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedJob> claimDue() {
        Instant now = Instant.now();
        List<ProcessingJob> due = jobs.findDueForUpdate(now, now.minus(properties.staleAfter()),
                Limit.of(properties.batchSize()));

        return due.stream().peek(ProcessingJob::markStarted).map(ClaimedJob::from).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId) {
        jobs.findById(jobId).ifPresent(ProcessingJob::markCompleted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, String errorMessage) {
        jobs.findById(jobId).ifPresent(job -> job.markFailed(errorMessage));
    }
}
