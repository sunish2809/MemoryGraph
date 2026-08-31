package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    /**
     * Finds work that is due, and locks it so no other worker takes the same job.
     * <p>
     * Three things count as due: a job that has never run, a failed job with attempts to spare whose
     * backoff has elapsed, and a job left in {@code PROCESSING} by a worker that died — without that
     * last case a crash would strand work forever.
     * <p>
     * {@code SKIP LOCKED} (the {@code -2} lock timeout hint) lets several application instances sweep
     * at the same time without blocking each other or double-processing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select j from ProcessingJob j
            where (j.status = com.memorygraph.backend.memory.domain.ProcessingStatus.PENDING
                     and j.nextAttemptAt <= :now)
               or (j.status = com.memorygraph.backend.memory.domain.ProcessingStatus.FAILED
                     and j.attempts < j.maxAttempts
                     and j.nextAttemptAt <= :now)
               or (j.status = com.memorygraph.backend.memory.domain.ProcessingStatus.PROCESSING
                     and j.startedAt < :staleBefore)
            order by j.nextAttemptAt asc
            """)
    List<ProcessingJob> findDueForUpdate(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore,
            Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select j from ProcessingJob j where j.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") UUID id);
}
