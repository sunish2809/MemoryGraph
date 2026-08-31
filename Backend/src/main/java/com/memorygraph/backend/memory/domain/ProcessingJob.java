package com.memorygraph.backend.memory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One unit of asynchronous enrichment work, persisted rather than held in memory.
 * <p>
 * Persisting the job is what keeps the processing architecture open: work survives a restart, can be
 * retried with backoff, is observable per user, and can later be handed to an out-of-process worker
 * (or a Kafka consumer) without changing how it is produced.
 * <p>
 * {@code userId} is denormalised from the memory so jobs can be listed and audited per user without
 * a join, and so a log line always carries the owner.
 */
@Entity
@Table(name = "processing_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48, updatable = false)
    private ProcessingJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProcessingStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /** Earliest time this job may be picked up. Moves forward on each failed attempt. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ProcessingJob(UUID memoryId, UUID userId, ProcessingJobType type) {
        this.memoryId = memoryId;
        this.userId = userId;
        this.type = type;
        this.status = ProcessingStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.nextAttemptAt = Instant.now();
    }

    public static ProcessingJob pending(UUID memoryId, UUID userId, ProcessingJobType type) {
        return new ProcessingJob(memoryId, userId, type);
    }

    public void markStarted() {
        this.status = ProcessingStatus.PROCESSING;
        this.attempts += 1;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.durationMs = null;
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = ProcessingStatus.COMPLETED;
        finish();
    }

    /**
     * Records a failure. The job stays retryable until the attempt budget is spent, with the delay
     * doubling each time so a persistently broken input does not spin.
     */
    public void markFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        finish();
        this.nextAttemptAt = Instant.now().plus(FIRST_RETRY_DELAY.multipliedBy(1L << (attempts - 1)));
    }

    public boolean hasExhaustedAttempts() {
        return attempts >= maxAttempts;
    }

    private void finish() {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        }
    }
}
