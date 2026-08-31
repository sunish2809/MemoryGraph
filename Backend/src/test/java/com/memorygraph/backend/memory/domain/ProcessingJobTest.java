package com.memorygraph.backend.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The retry policy in isolation. Worth testing directly: it decides whether a transient failure
 * eventually succeeds or a permanently broken input spins forever.
 */
class ProcessingJobTest {

    @Test
    void startsPendingAndDueImmediately() {
        ProcessingJob job = newJob();

        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void recordsHowLongASuccessfulAttemptTook() {
        ProcessingJob job = newJob();

        job.markStarted();
        job.markCompleted();

        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void staysRetryableWhileAttemptsRemain() {
        ProcessingJob job = newJob();

        job.markStarted();
        job.markFailed("storage unavailable");

        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("storage unavailable");
        assertThat(job.hasExhaustedAttempts()).isFalse();
    }

    @Test
    void stopsRetryingOnceTheAttemptBudgetIsSpent() {
        ProcessingJob job = newJob();

        for (int attempt = 0; attempt < job.getMaxAttempts(); attempt++) {
            job.markStarted();
            job.markFailed("still broken");
        }

        assertThat(job.hasExhaustedAttempts()).isTrue();
    }

    /** Backoff, so a persistently failing input does not consume a worker in a tight loop. */
    @Test
    void waitsLongerBeforeEachSubsequentRetry() {
        ProcessingJob job = newJob();

        job.markStarted();
        job.markFailed("first failure");
        Instant afterFirstFailure = job.getNextAttemptAt();

        job.markStarted();
        job.markFailed("second failure");

        assertThat(job.getNextAttemptAt()).isAfter(afterFirstFailure);
    }

    @Test
    void clearsTheErrorOfAPreviousAttemptWhenRetried() {
        ProcessingJob job = newJob();

        job.markStarted();
        job.markFailed("transient glitch");
        job.markStarted();

        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(job.getErrorMessage()).isNull();
    }

    private ProcessingJob newJob() {
        return ProcessingJob.pending(UUID.randomUUID(), UUID.randomUUID(), ProcessingJobType.MEDIA_METADATA);
    }
}
