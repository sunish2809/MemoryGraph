package com.memorygraph.backend.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.ai.AiProperties;

/**
 * A stand-in embedding model for environments without an API key.
 * <p>
 * Same normalised text always yields the same unit vector; different text almost always yields a
 * different one. That is enough to prove the storage, hybrid ranking and ask plumbing work. It is
 * not semantic similarity — "Sikkim trip" and "journey to the Himalayas" will not match — and must
 * never be used as if it were.
 * <p>
 * Gated on {@code spring.ai.model.embedding} (not {@code @ConditionalOnMissingBean(EmbeddingModel)})
 * because component-scan conditions run before Spring AI auto-config registers the model bean.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "none", matchIfMissing = true)
class HashingEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    HashingEmbeddingClient(AiProperties properties) {
        this.dimensions = properties.dimensions();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Cannot embed blank text");
        }
        byte[] seed = sha256(text.strip().toLowerCase(Locale.ROOT));
        float[] vector = new float[dimensions];
        // Expand the 32-byte digest into the full dimension with a simple LCG so every slot is filled
        // and identical inputs stay identical across JVMs.
        long state = bytesToLong(seed);
        double normSquared = 0.0;
        for (int i = 0; i < dimensions; i++) {
            state = state * 6364136223846793005L + 1L;
            // Map the full 64-bit state into (-1, 1). An earlier version only used the high bits in a
            // way that kept every component negative, which piled every vector into one orthant and
            // made unrelated texts look spuriously similar under cosine distance.
            float value = (float) (state / (double) Long.MAX_VALUE);
            vector[i] = value;
            normSquared += (double) value * value;
        }
        float norm = (float) Math.sqrt(normSquared);
        if (norm > 0f) {
            for (int i = 0; i < dimensions; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static long bytesToLong(byte[] bytes) {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[i] & 0xffL);
        }
        return value == 0L ? 1L : value;
    }
}
