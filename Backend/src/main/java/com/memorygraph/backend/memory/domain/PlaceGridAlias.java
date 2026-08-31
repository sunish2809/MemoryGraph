package com.memorygraph.backend.memory.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Extra GPS-grid keys that should resolve to an existing place after a merge. Without this, a new
 * photo in a merged cell would recreate the duplicate ("Gangtok" vs the neighbouring 1 km cell).
 */
@Entity
@Table(name = "place_grid_aliases")
@IdClass(PlaceGridAlias.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceGridAlias {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "normalized_key", nullable = false, updatable = false, length = 64)
    private String normalizedKey;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    private PlaceGridAlias(UUID userId, String normalizedKey, UUID placeId) {
        this.userId = userId;
        this.normalizedKey = normalizedKey;
        this.placeId = placeId;
    }

    public static PlaceGridAlias of(UUID userId, String normalizedKey, UUID placeId) {
        return new PlaceGridAlias(userId, normalizedKey, placeId);
    }

    public void reassign(UUID placeId) {
        this.placeId = placeId;
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID userId;
        private String normalizedKey;
    }
}
