package com.memorygraph.backend.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * One-time password-reset links. Mail is optional: when SMTP is not configured the link is written
 * to the server log so an operator can help a tester, rather than failing the request.
 */
@ConfigurationProperties(prefix = "memorygraph.password-reset")
public record PasswordResetProperties(Duration tokenTtl, String publicBaseUrl, String mailFrom) {

    public Duration tokenTtlOrDefault() {
        return tokenTtl == null ? Duration.ofHours(1) : tokenTtl;
    }

    public String publicBaseUrlOrDefault() {
        String value = publicBaseUrl == null ? "" : publicBaseUrl.strip();
        if (!StringUtils.hasText(value)) {
            return "http://localhost:3000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String mailFromOrDefault() {
        return StringUtils.hasText(mailFrom) ? mailFrom.strip() : "MemoryGraph <noreply@localhost>";
    }
}
