package com.memorygraph.backend.memory.domain;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memory_places")
@IdClass(MemoryPlaceLink.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryPlaceLink {

    @Id
    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Id
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    private MemoryPlaceLink(UUID memoryId, UUID placeId) {
        this.memoryId = memoryId;
        this.placeId = placeId;
    }

    public static MemoryPlaceLink of(UUID memoryId, UUID placeId) {
        return new MemoryPlaceLink(memoryId, placeId);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID memoryId;
        private UUID placeId;
    }
}
