package com.memorygraph.backend.memory.application.processing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "memorygraph.processing")
public record ProcessingProperties(

        /** How often the sweeper looks for due work. */
        @NotNull Duration sweepInterval,

        /** Maximum jobs claimed per sweep, which bounds how much work one instance takes on at once. */
        @Min(1) int batchSize,

        /**
         * How long a job may sit in {@code PROCESSING} before it is assumed abandoned by a dead worker
         * and reclaimed. Must comfortably exceed the slowest expected job.
         */
        @NotNull Duration staleAfter,

        /** Threads available for enrichment. Bounded, because decoding media is CPU-bound. */
        @Min(1) int workerThreads,

        @Min(0) int queueCapacity) {
}
