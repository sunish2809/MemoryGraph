package com.memorygraph.backend.auth.security;

import java.time.Instant;
import java.util.UUID;

/** A bearer token that passed signature, issuer and expiry checks. */
public record VerifiedAccessToken(UUID userId, Instant issuedAt) {
}
