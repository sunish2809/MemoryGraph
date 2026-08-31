package com.memorygraph.backend.auth.security;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.memorygraph.backend.common.logging.RequestContext;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a {@code Authorization: Bearer <jwt>} header into an authenticated security context.
 * <p>
 * A bad or missing token never fails the request here: the context is simply left anonymous and the
 * authorization rules decide whether that is acceptable, which keeps public endpoints reachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractBearerToken(request).ifPresent(token -> authenticate(token, request));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        UUID userId;
        try {
            userId = jwtService.verifyAndExtractUserId(token);
        } catch (InvalidTokenException ex) {
            log.debug("Rejected bearer token: {}", ex.getMessage());
            return;
        }

        // Re-read the account on every request so deactivated users lose access immediately rather
        // than when their token happens to expire.
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isEnabled()) {
            log.debug("Bearer token references a missing or disabled user: {}", userId);
            return;
        }

        AuthenticatedUser principal = AuthenticatedUser.from(user.get());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put(RequestContext.USER_ID_KEY, principal.id().toString());
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
