package com.memorygraph.backend.memory.application.processing;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The guarantee behind the fast path: polls for work that is due and runs it.
 * <p>
 * This is what makes the pipeline recoverable — jobs that were never dispatched, jobs that failed and
 * have retries left, and jobs abandoned by a worker that died are all picked up here. It is also the
 * seam where a message broker would slot in: replace this poller with a consumer and nothing else in
 * the pipeline changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingJobSweeper {

    private final ProcessingJobTransitions transitions;
    private final ProcessingJobRunner runner;

    @Scheduled(fixedDelayString = "${memorygraph.processing.sweep-interval}")
    public void sweep() {
        List<ClaimedJob> claimed = transitions.claimDue();
        if (claimed.isEmpty()) {
            return;
        }

        log.info("Sweeper claimed {} due processing job(s)", claimed.size());
        claimed.forEach(runner::runClaimed);
    }
}
