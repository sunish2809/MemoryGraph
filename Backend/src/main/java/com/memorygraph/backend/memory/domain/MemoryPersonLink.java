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

@Entity
@Table(name = "memory_people")
@IdClass(MemoryPersonLink.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryPersonLink {

    @Id
    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Id
    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    private MemoryPersonLink(UUID memoryId, UUID personId) {
        this.memoryId = memoryId;
        this.personId = personId;
    }

    public static MemoryPersonLink of(UUID memoryId, UUID personId) {
        return new MemoryPersonLink(memoryId, personId);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID memoryId;
        private UUID personId;
    }
}
