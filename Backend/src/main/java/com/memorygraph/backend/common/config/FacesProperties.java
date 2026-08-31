package com.memorygraph.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "memorygraph.faces")
public record FacesProperties(
        boolean enabled,
        @NotBlank String serviceUrl,
        /**
         * Maximum cosine distance for an auto-suggestion (pgvector {@code <=>}). Lower is closer;
         * ~0.4 is a reasonable InsightFace starting point.
         */
        @DecimalMin("0.0") @DecimalMax("2.0") double matchThreshold,
        /** Distance under which two unnamed faces are treated as the same unknown person. */
        @DefaultValue("0.5") @DecimalMin("0.0") @DecimalMax("2.0") double clusterThreshold,
        @DefaultValue("8") @Min(1) @Max(24) int videoMaxFrames,
        @DefaultValue("4") @Min(1) @Max(60) int videoFrameIntervalSeconds) {
}
