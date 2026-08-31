package com.memorygraph.backend.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.memorygraph.backend.ai.AiProperties;

class HashingEmbeddingClientTest {

    private final EmbeddingClient client = new HashingEmbeddingClient(
            new AiProperties("text-embedding-3-small", "gpt-4o-mini", 32, 5, 0.0, 0.45));

    @Test
    void isDeterministicForTheSameText() {
        assertThat(client.embed("Sikkim trip")).isEqualTo(client.embed("Sikkim trip"));
    }

    @Test
    void normalisesCaseAndWhitespace() {
        assertThat(client.embed("Sikkim trip")).isEqualTo(client.embed("  sikkim TRIP  "));
    }

    @Test
    void differsForDifferentText() {
        assertThat(client.embed("Sikkim trip")).isNotEqualTo(client.embed("Tax return"));
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> client.embed("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void producesAUnitVectorOfTheConfiguredWidth() {
        float[] vector = client.embed("anything");
        assertThat(vector).hasSize(32);
        double norm = 0.0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void unrelatedTextsAreFarApart() {
        float[] trip = client.embed("Sikkim trip\nThe train to Gangtok was delayed.");
        float[] tax = client.embed("Tax return\nFiled the paperwork.");
        double distance = cosineDistance(trip, tax);
        assertThat(distance).isGreaterThan(0.5);
    }

    private static double cosineDistance(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return 1.0 - dot;
    }
}
