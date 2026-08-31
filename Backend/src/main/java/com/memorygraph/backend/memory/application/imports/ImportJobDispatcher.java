package com.memorygraph.backend.memory.application.imports;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memorygraph.backend.memory.application.processing.ProcessingConfig;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fast path after commit, plus a sweep for imports that missed the event (same idea as processing
 * jobs).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportJobDispatcher {

    private final ImportJobTransitions transitions;
    private final ImportJobRunner runner;
    private final ImportJobRepository jobs;

    @Async(ProcessingConfig.EXECUTOR_BEAN_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueued(ImportJobQueued event) {
        transitions.claim(event.jobId()).ifPresentOrElse(runner::run,
                () -> log.debug("Import job {} was already claimed elsewhere", event.jobId()));
    }

    @Scheduled(fixedDelayString = "${memorygraph.processing.sweep-interval}")
    public void sweep() {
        for (ImportJob job : jobs.findDuePending()) {
            transitions.claim(job.getId()).ifPresent(runner::run);
        }
    }
}
