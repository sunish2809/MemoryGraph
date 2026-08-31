package com.memorygraph.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.auth.api.dto.AuthenticationResponse;
import com.memorygraph.backend.auth.api.dto.LoginRequest;
import com.memorygraph.backend.auth.api.dto.RegisterRequest;
import com.memorygraph.backend.auth.api.dto.RegistrationOptionsResponse;
import com.memorygraph.backend.auth.api.dto.UserResponse;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistrationProperties registration;

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
}
