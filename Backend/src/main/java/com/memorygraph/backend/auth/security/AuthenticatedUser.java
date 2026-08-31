package com.memorygraph.backend.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.memorygraph.backend.user.domain.User;

/**
 * Security principal. Exposes the user id because every data access path in the application is
 * scoped by owner.
 */
public record AuthenticatedUser(UUID id, String email, String passwordHash, boolean active) implements UserDetails {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Roles are not part of the MVP: every authenticated user owns exactly their own data.
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
