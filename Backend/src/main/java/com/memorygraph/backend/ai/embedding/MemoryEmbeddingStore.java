package com.memorygraph.backend.ai.embedding;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes the {@code memories.embedding} column.
 * <p>
 * Kept as JDBC rather than JPA because pgvector's {@code vector} type is not something we want the
 * entity model to know about. The only callers are the embedding processor and the semantic searcher.
 */
@Repository
public class MemoryEmbeddingStore {

    private final JdbcTemplate jdbc;

    public MemoryEmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID memoryId, float[] embedding) {
        jdbc.update("update memories set embedding = cast(? as vector) where id = ?",
                toVectorLiteral(embedding), memoryId);
    }

    public boolean exists(UUID memoryId) {
        Boolean present = jdbc.queryForObject(
                "select embedding is not null from memories where id = ?", Boolean.class, memoryId);
        return Boolean.TRUE.equals(present);
    }

    public void clear(UUID memoryId) {
        jdbc.update("update memories set embedding = null where id = ?", memoryId);
    }

    /** pgvector's text input form: {@code [0.1,0.2,...]}. */
    public static String toVectorLiteral(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            // Locale.ROOT so a comma-decimal locale cannot produce an unparseable literal.
            joiner.add(String.format(Locale.ROOT, "%.8f", value));
        }
        return joiner.toString();
    }
}
