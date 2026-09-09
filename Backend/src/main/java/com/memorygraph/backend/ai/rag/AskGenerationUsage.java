package com.memorygraph.backend.ai.rag;

import java.io.Serializable;
import java.time.LocalDate;
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
@Table(name = "ask_generation_usage")
@IdClass(AskGenerationUsage.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AskGenerationUsage {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID userId;
        private LocalDate periodStart;
    }
}
