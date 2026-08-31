package com.memorygraph.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Closed-beta registration. When {@code inviteCode} is blank, anyone may create an account (local
 * development). When it is set, {@code POST /auth/register} requires the same string.
 */
@ConfigurationProperties(prefix = "memorygraph.registration")
public record RegistrationProperties(String inviteCode) {

    public boolean inviteRequired() {
        return StringUtils.hasText(inviteCode);
    }

    public String requiredCode() {
        return inviteCode == null ? "" : inviteCode.strip();
    }
}
