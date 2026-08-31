package com.memorygraph.backend.memory.application.processing;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Enqueues an embedding job and publishes the event that wakes the fast path.
 * <p>
 * Extracted so text-memory creation and media-metadata completion share one code path: forgetting to
 * publish the event from one of them is how embeddings silently stop being written.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingJobEnqueuer {

    private final ProcessingJobRepository jobs;
    private final ApplicationEventPublisher events;

    @Transactional
    public void enqueue(UUID memoryId, UUID userId) {
        ProcessingJob job = jobs.save(ProcessingJob.pending(memoryId, userId, ProcessingJobType.EMBEDDING));
        events.publishEvent(new ProcessingJobQueued(job.getId()));
    }
}
