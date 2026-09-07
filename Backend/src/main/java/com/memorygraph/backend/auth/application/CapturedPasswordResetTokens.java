package com.memorygraph.backend.auth.application;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Holds the most recent raw reset token per email so integration tests can complete the flow
 * without SMTP. Not loaded outside the {@code test} profile.
 */
@Component
@Profile("test")
public class CapturedPasswordResetTokens {

    private final ConcurrentHashMap<String, String> lastTokenByEmail = new ConcurrentHashMap<>();

    public void record(String email, String rawToken) {
        lastTokenByEmail.put(email, rawToken);
    }

    public String lastToken(String email) {
        return lastTokenByEmail.get(email);
    }
}
