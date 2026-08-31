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
 * A “Looks like X” the owner dismissed. Auto-suggest will not offer that person on this face again.
 */
@Entity
@Table(name = "face_suggestion_rejections")
@IdClass(FaceSuggestionRejection.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceSuggestionRejection {

    @Id
    @Column(name = "face_id", nullable = false, updatable = false)
    private UUID faceId;

    @Id
    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    private FaceSuggestionRejection(UUID faceId, UUID personId) {
        this.faceId = faceId;
        this.personId = personId;
    }

    public static FaceSuggestionRejection of(UUID faceId, UUID personId) {
        return new FaceSuggestionRejection(faceId, personId);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID faceId;
        private UUID personId;
    }
}
