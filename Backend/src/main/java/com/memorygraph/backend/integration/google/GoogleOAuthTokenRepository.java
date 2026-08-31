package com.memorygraph.backend.integration.google;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleOAuthTokenRepository extends JpaRepository<GoogleOAuthToken, UUID> {
}
