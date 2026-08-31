package com.memorygraph.backend.auth.security;

import java.time.Instant;

/** A signed access token together with the instant it stops being valid. */
public record AccessToken(String value, Instant expiresAt) {
}
