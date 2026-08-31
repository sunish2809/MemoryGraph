package com.memorygraph.backend.auth.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

/**
 * Access to the caller's identity. Application services take the owner id as an explicit argument;
 * this helper exists so controllers can supply it without reaching into Spring Security details.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID requireId() {
        return require().id();
    }

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "No authenticated user in the security context");
        }
        return principal;
    }
}
