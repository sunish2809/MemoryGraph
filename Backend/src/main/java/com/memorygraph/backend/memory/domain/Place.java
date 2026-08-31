package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A place on the owner's map, clustered from photo GPS (~1 km cells). Display names start as
 * coordinates; reverse-geocoding refines them (e.g. "Gangtok") without changing identity keys.
 */
@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    /** ~0.01° ≈ 1 km — keeps nearby photos on one place without a geocoder. */
    private static final double GRID = 0.01;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "normalized_key", nullable = false, length = 64, updatable = false)
    private String normalizedKey;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "geocoded_at")
    private Instant geocodedAt;

    @Column(name = "name_locked", nullable = false)
    private boolean nameLocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Place(UUID userId, String displayName, String normalizedKey, double latitude, double longitude) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.displayName = displayName;
        this.normalizedKey = normalizedKey;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Place create(UUID userId, double latitude, double longitude) {
        String key = gridKey(latitude, longitude);
        double gridLat = Math.rint(latitude / GRID) * GRID;
        double gridLon = Math.rint(longitude / GRID) * GRID;
        return new Place(userId, formatName(gridLat, gridLon), key, gridLat, gridLon);
    }

    public static String gridKey(double latitude, double longitude) {
        long latCell = Math.round(latitude / GRID);
        long lonCell = Math.round(longitude / GRID);
        return latCell + ":" + lonCell;
    }

    /** Applies a reverse-geocoded label once; identity ({@link #normalizedKey}) stays fixed. */
    public void applyGeocodedName(String name) {
        if (nameLocked || name == null || name.isBlank()) {
            return;
        }
        this.displayName = name.strip().length() <= 255 ? name.strip() : name.strip().substring(0, 255);
        this.geocodedAt = Instant.now();
    }

    public void rename(String displayName) {
        String trimmed = displayName.strip();
        this.displayName = trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
        this.nameLocked = true;
        if (this.geocodedAt == null) {
            this.geocodedAt = Instant.now();
        }
    }

    public boolean needsGeocode() {
        return geocodedAt == null && !nameLocked;
    }

    private static String formatName(double latitude, double longitude) {
        String ns = latitude >= 0 ? "N" : "S";
        String ew = longitude >= 0 ? "E" : "W";
        return String.format(Locale.ROOT, "%.2f°%s, %.2f°%s", Math.abs(latitude), ns, Math.abs(longitude), ew);
    }
}
