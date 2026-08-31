package com.memorygraph.backend.memory.domain;

import java.time.Instant;
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
 * A named stretch of days. Places and people on the trip are derived from memories that fall inside
 * {@code startedAt}–{@code endedAt}, not stored as a separate join.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Trip(UUID userId, String title, Instant startedAt, Instant endedAt, String notes) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.title = title;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.notes = notes;
    }

    public static Trip create(UUID userId, String title, Instant startedAt, Instant endedAt, String notes) {
        return new Trip(userId, trimTitle(title), startedAt, endedAt, blankToNull(notes));
    }

    public void edit(String title, Instant startedAt, Instant endedAt, String notes) {
        if (title != null) {
            this.title = trimTitle(title);
        }
        if (startedAt != null) {
            this.startedAt = startedAt;
        }
        if (endedAt != null) {
            this.endedAt = endedAt;
        }
        if (notes != null) {
            this.notes = blankToNull(notes);
        }
    }

    private static String trimTitle(String title) {
        String trimmed = title.strip();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private static String blankToNull(String notes) {
        return StringUtils.hasText(notes) ? notes.strip() : null;
    }
}
