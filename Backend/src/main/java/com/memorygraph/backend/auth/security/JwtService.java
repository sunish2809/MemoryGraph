package com.memorygraph.backend.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memorygraph.backend.common.config.MemoryGraphProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Issues and verifies HMAC-SHA256 access tokens.
 * <p>
 * Tokens are self-contained and short lived. There is no server-side revocation list, so the
 * access-token TTL is also the worst-case window during which a disabled account could still
 * present a valid token; the authentication filter re-checks the account on every request to close
 * that gap.
 */
@Service
public class JwtService {

    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;
    private static final String EMAIL_CLAIM = "email";

    private final byte[] secret;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final DefaultJWTProcessor<SecurityContext> processor;

    public JwtService(MemoryGraphProperties properties) {
        MemoryGraphProperties.Jwt jwt = properties.security().jwt();
        this.secret = jwt.secret().getBytes(StandardCharsets.UTF_8);
        this.issuer = jwt.issuer();
        this.accessTokenTtl = jwt.accessTokenTtl();
        this.processor = buildProcessor(this.secret, this.issuer);
    }

    public AccessToken issue(UUID userId, String email) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim(EMAIL_CLAIM, email)
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(ALGORITHM), claims);
        try {
            signedJwt.sign(new MACSigner(secret));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign access token", ex);
        }
        return new AccessToken(signedJwt.serialize(), expiresAt);
    }

    /**
     * Verifies the signature, issuer and expiry, and returns the user id the token was issued for.
     *
     * @throws InvalidTokenException if the token cannot be trusted
     */
    public UUID verifyAndExtractUserId(String token) {
        JWTClaimsSet claims;
        try {
            claims = processor.process(token, null);
        } catch (Exception ex) {
            throw new InvalidTokenException("Access token could not be verified", ex);
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Access token subject is not a valid user id", ex);
        }
    }

    private static DefaultJWTProcessor<SecurityContext> buildProcessor(byte[] secret, String issuer) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALGORITHM, new ImmutableSecret<>(secret)));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuer).build(),
                Set.of("sub", "exp")));
        return processor;
    }
}
