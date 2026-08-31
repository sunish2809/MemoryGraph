package com.memorygraph.backend.integration.google;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.memorygraph.backend.common.config.MemoryGraphProperties;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GoogleOAuthService {

    public static final String PICKER_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly";

    private static final long STATE_TTL_SECONDS = 600;

    private final GoogleProperties google;
    private final MemoryGraphProperties app;
    private final GoogleOAuthTokenRepository tokens;
    private final RestClient http;

    public GoogleOAuthService(GoogleProperties google, MemoryGraphProperties app, GoogleOAuthTokenRepository tokens) {
        this.google = google;
        this.app = app;
        this.tokens = tokens;
        this.http = RestClient.builder().build();
    }

    public boolean configured() {
        return google.configured();
    }

    @Transactional(readOnly = true)
    public boolean connected(UUID userId) {
        return tokens.existsById(userId);
    }

    public String authorizationUrl(UUID userId) {
        requireConfigured();
        String state = signState(userId);
        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", google.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", PICKER_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    @Transactional
    public void exchangeCode(UUID userId, String code, String state) {
        requireConfigured();
        verifyState(userId, state);
        TokenResponse token = requestToken(form -> {
            form.add("code", code);
            form.add("grant_type", "authorization_code");
            form.add("redirect_uri", google.redirectUri());
        });
        persist(userId, token);
    }

    @Transactional
    public void disconnect(UUID userId) {
        tokens.deleteById(userId);
    }

    /**
     * Returns a usable access token, refreshing when close to expiry. Throws if the user has not
     * connected Google or refresh fails.
     */
    @Transactional
    public String requireAccessToken(UUID userId) {
        requireConfigured();
        GoogleOAuthToken stored = tokens.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Connect Google Photos first (Import → Connect Google)"));
        Instant now = Instant.now();
        if (!stored.accessTokenExpired(now)) {
            return stored.getAccessToken();
        }
        if (stored.getRefreshToken() == null || stored.getRefreshToken().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Google session expired. Disconnect and connect again.");
        }
        TokenResponse refreshed = requestToken(form -> {
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", stored.getRefreshToken());
        });
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, refreshed.expiresIn()));
        stored.replaceTokens(refreshed.accessToken(), refreshed.refreshToken(), expiresAt,
                refreshed.scope() != null ? refreshed.scope() : stored.getScope());
        return stored.getAccessToken();
    }

    private void persist(UUID userId, TokenResponse token) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, token.expiresIn()));
        String scope = token.scope() != null && !token.scope().isBlank() ? token.scope() : PICKER_SCOPE;
        tokens.findById(userId).ifPresentOrElse(
                existing -> existing.replaceTokens(token.accessToken(), token.refreshToken(), expiresAt, scope),
                () -> tokens.save(GoogleOAuthToken.create(userId, token.accessToken(), token.refreshToken(),
                        expiresAt, scope)));
    }

    private TokenResponse requestToken(java.util.function.Consumer<MultiValueMap<String, String>> extra) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        extra.accept(form);
        try {
            TokenResponse body = http.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (body == null || body.accessToken() == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Google token response was empty");
            }
            return body;
        } catch (RestClientException ex) {
            log.warn("Google OAuth token exchange failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Could not complete Google sign-in", ex);
        }
    }

    private void requireConfigured() {
        if (!google.configured()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Google Photos Picker is not configured (set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET)");
        }
    }

    private String signState(UUID userId) {
        String payload = userId + "|" + Instant.now().getEpochSecond() + "|" + UUID.randomUUID();
        return base64Url(payload.getBytes(StandardCharsets.UTF_8)) + "." + hmac(payload);
    }

    private void verifyState(UUID userId, String state) {
        if (state == null || !state.contains(".")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid OAuth state");
        }
        int dot = state.indexOf('.');
        String payloadB64 = state.substring(0, dot);
        String sig = state.substring(dot + 1);
        String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        if (!hmac(payload).equals(sig)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid OAuth state signature");
        }
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid OAuth state payload");
        }
        if (!userId.toString().equals(parts[0])) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "OAuth state does not match the signed-in user");
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid OAuth state timestamp");
        }
        if (Instant.now().getEpochSecond() - issuedAt > STATE_TTL_SECONDS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "OAuth state expired; try Connect Google again");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(app.security().jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign OAuth state", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            String scope,
            @JsonProperty("token_type") String tokenType) {
    }

}
