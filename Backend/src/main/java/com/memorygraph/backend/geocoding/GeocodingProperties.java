package com.memorygraph.backend.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "memorygraph.geocoding")
public record GeocodingProperties(
        boolean enabled,
        @NotBlank String nominatimUrl,
        @NotBlank String userAgent) {
}
