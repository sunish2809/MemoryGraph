package com.memorygraph.backend.auth.api.dto;

import java.time.Instant;

/** Result of a successful registration or login. */
public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user) {

    public static AuthenticationResponse of(String accessToken, Instant expiresAt, UserResponse user) {
        return new AuthenticationResponse(accessToken, "Bearer", expiresAt, user);
    }
}
