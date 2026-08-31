package com.memorygraph.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Application-owned AI settings that sit beside Spring AI's own properties.
 * <p>
 * Model selection for the live OpenAI beans is configured under {@code spring.ai.openai.*}. These
 * properties cover what our code needs regardless of provider: vector width, how many memories Ask
 * retrieves, and the labels returned in API responses.
 */
@Validated
@ConfigurationProperties(prefix = "memorygraph.ai")
public record AiProperties(
        @NotBlank String embeddingModel,
        @NotBlank String chatModel,
        @Positive int dimensions,
        @Min(1) @Max(50) int askTopK,
        /** Zero is allowed: tests want deterministic, non-creative answers. */
        @DecimalMin("0.0") double chatTemperature,
        /**
         * Maximum cosine distance for a semantic hit. pgvector's {@code <=>} is
         * {@code 1 - cosine_similarity}, so 0 is identical and 2 is opposite. Without a ceiling,
         * every query returns the nearest memory even when nothing is related — which would make
         * stop-word and empty-phrase searches look like hits.
         */
        @DecimalMin("0.0") double maxSemanticDistance) {
}
