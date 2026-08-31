package com.memorygraph.backend.ai.embedding;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.memory.application.processing.MemoryProcessor;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Embeds a memory's searchable text and stores the vector.
 * <p>
 * Skips blank content rather than failing: a photo that has not yet had its filename folded into
 * {@code content} (or a note that somehow arrived empty) is not embeddable yet, and failing the job
 * would only burn retries. The sweeper will not re-queue a completed skip; a later enrichment that
 * adds text can enqueue a fresh embedding job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingProcessor implements MemoryProcessor {

    private final EmbeddingClient embeddings;
    private final MemoryEmbeddingStore store;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.EMBEDDING;
    }

    @Override
    public void process(Memory memory) {
        String text = searchableText(memory);
        if (!StringUtils.hasText(text)) {
            log.debug("Skipping embedding for memory {}: no searchable text yet", memory.getId());
            store.clear(memory.getId());
            return;
        }
        float[] vector = embeddings.embed(text);
        store.save(memory.getId(), vector);
        log.debug("Stored {}-d embedding for memory {}", vector.length, memory.getId());
    }

    private static String searchableText(Memory memory) {
        StringBuilder builder = new StringBuilder();
        append(builder, memory.getTitle());
        append(builder, memory.getDescription());
        append(builder, memory.getContent());
        return builder.toString().strip();
    }

    private static void append(StringBuilder builder, String part) {
        if (StringUtils.hasText(part)) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(part.strip());
        }
    }
}
