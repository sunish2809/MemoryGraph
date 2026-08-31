package com.memorygraph.backend.memory.application.processing;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts enrichment as soon as an upload is safely committed, so a photo usually has its metadata by
 * the time the user's browser has finished rendering the response.
 * <p>
 * Listening after commit rather than during it matters: the job row must be visible to the worker,
 * which runs on a different thread and therefore a different connection. If this dispatch is missed —
 * because the process died, or the executor's queue was full — the job is still {@code PENDING} in the
 * database and {@link ProcessingJobSweeper} will pick it up. The fast path is an optimisation, never
 * the guarantee.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingJobDispatcher {

    private final ProcessingJobTransitions transitions;
    private final ProcessingJobRunner runner;

    @Async(ProcessingConfig.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueued(ProcessingJobQueued event) {
        transitions.claim(event.jobId()).ifPresentOrElse(runner::runClaimed,
                () -> log.debug("Processing job {} was already claimed elsewhere", event.jobId()));
    }
}
