package com.memorygraph.backend.memory.application.processing;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class ProcessingConfig {

    public static final String EXECUTOR_BEAN_NAME = "memoryProcessingExecutor";

    /**
     * A bounded pool rather than the application's default executor.
     * <p>
     * Enrichment decodes media and will later run models: unbounded concurrency there would let a
     * burst of uploads starve the threads serving HTTP requests. When the queue fills, the caller runs
     * the task, which pushes back on the producer instead of dropping work.
     */
    @Bean(name = EXECUTOR_BEAN_NAME)
    Executor memoryProcessingExecutor(ProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("memory-processing-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
