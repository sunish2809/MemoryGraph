package com.memorygraph.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.memorygraph.backend.common.config.MemoryGraphProperties;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-secret-with-enough-length";
    private static final UUID USER_ID = UUID.fromString("6f1c8d3e-1b2a-4c5d-8e9f-0a1b2c3d4e5f");

    private final JwtService jwtService = serviceWith(SECRET, "memorygraph", Duration.ofMinutes(30));

    @Test
    void issuedTokenCanBeVerifiedAndCarriesTheUserId() {
        AccessToken token = jwtService.issue(USER_ID, "someone@example.com");

        assertThat(jwtService.verifyAndExtractUserId(token.value())).isEqualTo(USER_ID);
        assertThat(token.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService otherApplication = serviceWith("a-completely-different-signing-secret-value", "memorygraph",
                Duration.ofMinutes(30));
        String foreignToken = otherApplication.issue(USER_ID, "someone@example.com").value();

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(foreignToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService otherIssuer = serviceWith(SECRET, "somebody-else", Duration.ofMinutes(30));
        String foreignToken = otherIssuer.issue(USER_ID, "someone@example.com").value();

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(foreignToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService alreadyExpired = serviceWith(SECRET, "memorygraph", Duration.ofSeconds(-120));
        String staleToken = alreadyExpired.issue(USER_ID, "someone@example.com").value();

        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId(staleToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.verifyAndExtractUserId("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    private static JwtService serviceWith(String secret, String issuer, Duration ttl) {
        return new JwtService(new MemoryGraphProperties(
                new MemoryGraphProperties.Cors(List.of("http://localhost:5173")),
                new MemoryGraphProperties.Security(new MemoryGraphProperties.Jwt(secret, issuer, ttl))));
    }
}
