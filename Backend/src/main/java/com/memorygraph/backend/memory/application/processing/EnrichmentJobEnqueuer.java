package com.memorygraph.backend.memory.application.processing;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

import lombok.RequiredArgsConstructor;

/** Enqueues the next enrichment step and wakes the fast path. */
@Service
@RequiredArgsConstructor
public class EnrichmentJobEnqueuer {

    private final ProcessingJobRepository jobs;
    private final ApplicationEventPublisher events;

    @Transactional
    public void enqueue(UUID memoryId, UUID userId, ProcessingJobType type) {
        ProcessingJob job = jobs.save(ProcessingJob.pending(memoryId, userId, type));
        events.publishEvent(new ProcessingJobQueued(job.getId()));
    }
}
