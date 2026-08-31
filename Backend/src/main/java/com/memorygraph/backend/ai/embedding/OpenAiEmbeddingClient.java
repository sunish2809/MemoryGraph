package com.memorygraph.backend.ai.embedding;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.ai.AiProperties;

/**
 * Production embedding client: delegates to Spring AI's OpenAI model.
 * <p>
 * Marked {@code @Primary} when {@code spring.ai.model.embedding=openai}. Uses a property gate
 * (not {@code @ConditionalOnBean(EmbeddingModel)}) because component-scan conditions run before
 * Spring AI auto-config registers the model bean.
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai")
class OpenAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel model;
    private final AiProperties properties;

    OpenAiEmbeddingClient(EmbeddingModel model, AiProperties properties) {
        this.model = model;
        this.properties = properties;
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Cannot embed blank text");
        }
        var response = model.call(new EmbeddingRequest(List.of(text),
                OpenAiEmbeddingOptions.builder().model(properties.embeddingModel()).build()));
        float[] values = response.getResult().getOutput();
        if (values.length != properties.dimensions()) {
            throw new IllegalStateException("Embedding model returned " + values.length
                    + " dimensions; expected " + properties.dimensions()
                    + ". Check memorygraph.ai.dimensions against the configured model.");
        }
        return values;
    }
}
