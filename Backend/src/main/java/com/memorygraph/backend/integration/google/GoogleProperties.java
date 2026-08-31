package com.memorygraph.backend.integration.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Cloud OAuth client for Photos Picker. Blank client id/secret means the integration is
 * disabled (Takeout import still works).
 */
@ConfigurationProperties(prefix = "memorygraph.google")
public record GoogleProperties(String clientId, String clientSecret, String redirectUri) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
