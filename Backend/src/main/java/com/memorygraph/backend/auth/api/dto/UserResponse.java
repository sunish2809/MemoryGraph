package com.memorygraph.backend.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.user.domain.User;

public record UserResponse(UUID id, String email, String displayName, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
