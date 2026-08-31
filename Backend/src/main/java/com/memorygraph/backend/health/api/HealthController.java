package com.memorygraph.backend.health.api;

import java.time.Instant;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Lightweight public liveness check for the frontend and for local development.
 * <p>
 * Deep dependency checks (database, storage) stay on {@code /actuator/health}, which is what
 * container orchestration should probe.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/health")
public class HealthController {

    private final ObjectProvider<BuildProperties> buildProperties;

    public HealthController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    public record HealthStatus(String status, String application, String version, Instant timestamp) {
    }

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        String version = buildProperties.getIfAvailable() != null
                ? buildProperties.getIfAvailable().getVersion()
                : "dev";
        return ApiResponse.success(new HealthStatus("UP", "memorygraph-backend", version, Instant.now()));
    }
}
