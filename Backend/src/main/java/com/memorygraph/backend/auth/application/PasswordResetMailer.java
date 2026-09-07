package com.memorygraph.backend.auth.application;

/**
 * Delivers a one-time reset link. Implementations must not reveal whether the address has an
 * account — the caller already decided to notify this address.
 */
public interface PasswordResetMailer {

    void sendResetLink(String email, String resetUrl);
}
