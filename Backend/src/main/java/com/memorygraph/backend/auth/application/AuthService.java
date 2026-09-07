package com.memorygraph.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.auth.api.dto.AuthenticationResponse;
import com.memorygraph.backend.auth.api.dto.ForgotPasswordRequest;
import com.memorygraph.backend.auth.api.dto.LoginRequest;
import com.memorygraph.backend.auth.api.dto.MessageResponse;
import com.memorygraph.backend.auth.api.dto.RegisterRequest;
import com.memorygraph.backend.auth.api.dto.RegistrationOptionsResponse;
import com.memorygraph.backend.auth.api.dto.ResetPasswordRequest;
import com.memorygraph.backend.auth.api.dto.UserResponse;
import com.memorygraph.backend.auth.domain.PasswordResetToken;
import com.memorygraph.backend.auth.domain.PasswordResetTokenRepository;
import com.memorygraph.backend.auth.security.AccessToken;
import com.memorygraph.backend.auth.security.JwtService;
import com.memorygraph.backend.common.config.RegistrationProperties;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registration and login. Password verification lives here rather than in a Spring Security
 * {@code AuthenticationProvider} because the API returns a token, not a session, and this keeps the
 * flow and its error responses explicit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String FORGOT_PASSWORD_ACK =
            "If that email is registered, we sent password-reset instructions.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistrationProperties registration;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final PasswordResetProperties passwordReset;
    private final PasswordResetMailer passwordResetMailer;
    private final ObjectProvider<CapturedPasswordResetTokens> capturedResetTokens;

    @Transactional(readOnly = true)
    public RegistrationOptionsResponse registrationOptions() {
        return new RegistrationOptionsResponse(registration.inviteRequired());
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        requireInvite(request.inviteCode());
        String email = User.normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }

        User user = userRepository.save(
                User.register(email, passwordEncoder.encode(request.password()), request.displayName()));
        log.info("Registered user {}", user.getId());

        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        String email = User.normaliseEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> invalidCredentials(email));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(email);
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        log.info("Authenticated user {}", user.getId());
        return issueToken(user);
    }

    /**
     * Always acknowledges the same way, whether or not the address has an account, so this endpoint
     * cannot be used to enumerate users.
     */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = User.normaliseEmail(request.email());
        userRepository.findByEmail(email).filter(User::isEnabled).ifPresent(this::issuePasswordReset);
        return new MessageResponse(FORGOT_PASSWORD_ACK);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokens.findByTokenHash(hashResetToken(request.token()))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(AuthService::invalidResetToken);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(AuthService::invalidResetToken);
        if (!user.isEnabled()) {
            throw invalidResetToken();
        }

        user.changePasswordHash(passwordEncoder.encode(request.password()));
        token.markUsed(now);
        log.info("Reset password for user {}", user.getId());
        return new MessageResponse("Password updated. Sign in with your new password.");
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Confirms the signed-in user's password before a destructive action. The account is already
     * known, so a mismatch is reported as a wrong password rather than the login-time generic.
     */
    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Password is incorrect");
        }
    }

    private void issuePasswordReset(User user) {
        passwordResetTokens.deleteUnusedByUserId(user.getId());
        String rawToken = newResetToken();
        Instant expiresAt = Instant.now().plus(passwordReset.tokenTtlOrDefault());
        passwordResetTokens.save(PasswordResetToken.issue(user.getId(), hashResetToken(rawToken), expiresAt));

        String resetUrl = passwordReset.publicBaseUrlOrDefault() + "/reset-password?token=" + rawToken;
        passwordResetMailer.sendResetLink(user.getEmail(), resetUrl);
        capturedResetTokens.ifAvailable(captured -> captured.record(user.getEmail(), rawToken));
        log.info("Issued password-reset token for user {}", user.getId());
    }

    private AuthenticationResponse issueToken(User user) {
        AccessToken token = jwtService.issue(user.getId(), user.getEmail());
        return AuthenticationResponse.of(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    /**
     * Deliberately identical for an unknown email and a wrong password, so the API cannot be used to
     * discover which addresses have accounts.
     */
    private ApiException invalidCredentials(String email) {
        log.info("Failed login attempt for {}", email);
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    private static ApiException invalidResetToken() {
        return new ApiException(ErrorCode.RESET_TOKEN_INVALID, "This reset link is invalid or has expired");
    }

    private void requireInvite(String provided) {
        if (!registration.inviteRequired()) {
            return;
        }
        byte[] expected = registration.requiredCode().getBytes(StandardCharsets.UTF_8);
        byte[] given = provided == null ? new byte[0] : provided.strip().getBytes(StandardCharsets.UTF_8);
        if (expected.length != given.length || !MessageDigest.isEqual(expected, given)) {
            throw new ApiException(ErrorCode.INVITE_INVALID, "That invite code is not valid");
        }
    }

    private static String newResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashResetToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for password-reset tokens", ex);
        }
    }
}
