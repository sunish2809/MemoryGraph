package com.memorygraph.backend.integration.google;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "google_oauth_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleOAuthToken {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "scope", nullable = false, columnDefinition = "text")
    private String scope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private GoogleOAuthToken(UUID userId, String accessToken, String refreshToken, Instant expiresAt, String scope) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
        this.createdAt = Instant.now();
    }

    public static GoogleOAuthToken create(UUID userId, String accessToken, String refreshToken, Instant expiresAt,
            String scope) {
        return new GoogleOAuthToken(userId, accessToken, refreshToken, expiresAt, scope);
    }

    public void replaceTokens(String accessToken, String refreshToken, Instant expiresAt, String scope) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.refreshToken = refreshToken;
        }
        this.expiresAt = expiresAt;
        if (scope != null && !scope.isBlank()) {
            this.scope = scope;
        }
    }

    public boolean accessTokenExpired(Instant now) {
        return !expiresAt.isAfter(now.plusSeconds(60));
    }
}
