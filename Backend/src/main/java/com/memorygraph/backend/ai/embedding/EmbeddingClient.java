package com.memorygraph.backend.ai.embedding;

/**
 * Turns text into a fixed-length vector.
 * <p>
 * This is the seam the embedding provider sits behind. Production uses OpenAI; tests and deployments
 * without a key use a deterministic local hasher so the rest of the pipeline can still be exercised.
 * Callers must not assume the vector space of one implementation is comparable to another's.
 */
public interface EmbeddingClient {

    /** Dimension of every vector this client produces. */
    int dimensions();

    /**
     * Embeds non-blank text. Blank input is rejected rather than mapped to a zero vector, because a
     * zero vector would match every other zero and look like a hit.
     */
    float[] embed(String text);
}
