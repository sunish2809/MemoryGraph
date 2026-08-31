package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person who appears in the owner's archive. Today populated from WhatsApp senders; face
 * clustering can attach later without changing this identity row.
 */
@Entity
@Table(name = "people")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Person(UUID userId, String displayName, String normalizedName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.displayName = displayName;
        this.normalizedName = normalizedName;
    }

    public static Person create(UUID userId, String displayName) {
        String trimmed = displayName.strip();
        return new Person(userId, trimmed, normalise(trimmed));
    }

    public void rename(String displayName) {
        String trimmed = displayName.strip();
        this.displayName = trimmed;
        this.normalizedName = normalise(trimmed);
    }

    public static String normalise(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
