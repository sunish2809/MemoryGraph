package com.memorygraph.backend.memory.application.processing;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.memorygraph.backend.common.logging.RequestContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a job that has already been claimed, and records the outcome.
 * <p>
 * Never throws: a failure is a recorded state, not an exception for the caller to handle. That is what
 * lets the same method be driven from an event listener, from the retry sweeper, or from an
 * out-of-process worker later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingJobRunner {

    private static final int MAX_STORED_ERROR_LENGTH = 2000;

    private final MemoryEnrichmentStep enrichmentStep;
    private final ProcessingJobTransitions transitions;

    public void runClaimed(ClaimedJob job) {
        MDC.put(RequestContext.USER_ID_KEY, job.userId().toString());
        long startedAt = System.nanoTime();
        try {
            enrichmentStep.execute(job.jobId());
            transitions.complete(job.jobId());
            log.info("Processing job {} ({}) completed for memory {} in {}ms on attempt {}", job.jobId(), job.type(),
                    job.memoryId(), elapsedMs(startedAt), job.attempt());
        } catch (Exception ex) {
            handleFailure(job, ex, startedAt);
        } finally {
            MDC.remove(RequestContext.USER_ID_KEY);
        }
    }

    private void handleFailure(ClaimedJob job, Exception cause, long startedAt) {
        log.error("Processing job {} ({}) failed for memory {} after {}ms on attempt {}", job.jobId(), job.type(),
                job.memoryId(), elapsedMs(startedAt), job.attempt(), cause);
        try {
            transitions.fail(job.jobId(), truncate(describe(cause)));
            enrichmentStep.markMemoryFailed(job.jobId());
        } catch (Exception bookkeepingFailure) {
            // Losing the outcome would strand the job in PROCESSING; the sweeper's stale-job recovery
            // is the safety net, so this is logged rather than rethrown.
            log.error("Could not record failure of processing job {}", job.jobId(), bookkeepingFailure);
        }
    }

    private String describe(Exception cause) {
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private String truncate(String message) {
        return message.length() > MAX_STORED_ERROR_LENGTH ? message.substring(0, MAX_STORED_ERROR_LENGTH) : message;
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
