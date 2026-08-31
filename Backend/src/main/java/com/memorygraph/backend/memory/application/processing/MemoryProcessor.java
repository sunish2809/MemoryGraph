package com.memorygraph.backend.memory.application.processing;

import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

/**
 * One step of the enrichment pipeline. Implementations are looked up by {@link #jobType()}, so adding
 * a step (OCR, transcription, embeddings) means adding a bean and an enum constant, and nothing that
 * schedules or retries work has to change.
 * <p>
 * An implementation may throw to signal failure; the coordinator records the error and decides
 * whether to retry.
 */
public interface MemoryProcessor {

    ProcessingJobType jobType();

    void process(Memory memory);
}
