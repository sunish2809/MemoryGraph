package com.memorygraph.backend.memory.application.processing;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.memory.domain.ProcessingStatus;

/**
 * Runs the enrichment itself, in its own transaction, separate from the job's bookkeeping.
 * <p>
 * Splitting it out means a processor's changes to the memory commit or roll back as one unit, while
 * the job's own status is recorded either way.
 */
@Service
public class MemoryEnrichmentStep {

    private final MemoryRepository memories;
    private final ProcessingJobRepository jobs;
    private final Map<ProcessingJobType, MemoryProcessor> processors;
    private final EnrichmentJobEnqueuer enrichmentJobs;
    private final EmbeddingJobEnqueuer embeddingJobs;

    public MemoryEnrichmentStep(MemoryRepository memories, ProcessingJobRepository jobs,
            List<MemoryProcessor> processors, EnrichmentJobEnqueuer enrichmentJobs,
            EmbeddingJobEnqueuer embeddingJobs) {
        this.memories = memories;
        this.jobs = jobs;
        this.processors = processors.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(MemoryProcessor::jobType,
                        Function.identity()));
        this.enrichmentJobs = enrichmentJobs;
        this.embeddingJobs = embeddingJobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(UUID jobId) {
        ProcessingJob job = jobs.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job", jobId));

        Memory memory = memories.findById(job.getMemoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory", job.getMemoryId()));

        MemoryProcessor processor = processors.get(job.getType());
        if (processor == null) {
            throw new IllegalStateException("No processor registered for job type " + job.getType());
        }

        processor.process(memory);

        // Only media metadata owns processing_status. Later steps are optional enrichment.
        if (job.getType() == ProcessingJobType.MEDIA_METADATA) {
            memory.markProcessed(ProcessingStatus.COMPLETED);
            if (memory.getType() == MemoryType.AUDIO || memory.getType() == MemoryType.VIDEO) {
                enrichmentJobs.enqueue(memory.getId(), memory.getUserId(), ProcessingJobType.TRANSCRIPTION);
                if (memory.getType() == MemoryType.VIDEO) {
                    enrichmentJobs.enqueue(memory.getId(), memory.getUserId(), ProcessingJobType.FACE_DETECT);
                }
            } else {
                enrichmentJobs.enqueue(memory.getId(), memory.getUserId(), ProcessingJobType.OCR);
            }
        } else if (job.getType() == ProcessingJobType.OCR) {
            enrichmentJobs.enqueue(memory.getId(), memory.getUserId(), ProcessingJobType.CAPTION);
        } else if (job.getType() == ProcessingJobType.CAPTION) {
            if (memory.getType() == MemoryType.PHOTO) {
                enrichmentJobs.enqueue(memory.getId(), memory.getUserId(), ProcessingJobType.FACE_DETECT);
            }
            embeddingJobs.enqueue(memory.getId(), memory.getUserId());
        } else if (job.getType() == ProcessingJobType.TRANSCRIPTION) {
            embeddingJobs.enqueue(memory.getId(), memory.getUserId());
        } else if (job.getType() == ProcessingJobType.FACE_DETECT) {
            // Terminal optional step — nothing further.
        }
    }

    /**
     * Records that primary enrichment could not be completed. OCR / caption / embedding failures
     * deliberately do nothing here: the memory stays in whatever state media metadata left it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMemoryFailed(UUID jobId) {
        jobs.findById(jobId).ifPresent(job -> {
            if (job.getType() != ProcessingJobType.MEDIA_METADATA) {
                return;
            }
            memories.findById(job.getMemoryId())
                    .ifPresent(memory -> memory.markProcessed(ProcessingStatus.FAILED));
        });
    }
}
