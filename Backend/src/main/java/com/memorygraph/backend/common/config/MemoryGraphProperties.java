package com.memorygraph.backend.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Application-owned configuration. Validated at startup so a misconfigured deployment fails fast
 * instead of serving requests with, say, a weak signing key.
 */
@Validated
@ConfigurationProperties(prefix = "memorygraph")
public record MemoryGraphProperties(@Valid @NotNull Cors cors, @Valid @NotNull Security security) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Security(@Valid @NotNull Jwt jwt) {
    }

    public record Jwt(
            /** HMAC-SHA256 signing key. Must be at least 32 bytes to match the algorithm strength. */
            @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {
    }
}
