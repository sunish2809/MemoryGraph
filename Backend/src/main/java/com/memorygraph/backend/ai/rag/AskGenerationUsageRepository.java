package com.memorygraph.backend.ai.rag;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AskGenerationUsageRepository extends JpaRepository<AskGenerationUsage, AskGenerationUsage.Pk> {

    Optional<AskGenerationUsage> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);

    /**
     * Atomically spends one language-model Ask in the current period. Returns the new used count,
     * or empty when the row is already at {@code limit} (the OpenAI call must not run).
     */
    @Query(value = """
            INSERT INTO ask_generation_usage (user_id, period_start, used_count)
            VALUES (:userId, :periodStart, 1)
            ON CONFLICT (user_id, period_start)
            DO UPDATE SET used_count = ask_generation_usage.used_count + 1
            WHERE ask_generation_usage.used_count < :limit
            RETURNING used_count
            """, nativeQuery = true)
    Optional<Integer> tryIncrement(
            @Param("userId") UUID userId,
            @Param("periodStart") LocalDate periodStart,
            @Param("limit") int limit);
}
