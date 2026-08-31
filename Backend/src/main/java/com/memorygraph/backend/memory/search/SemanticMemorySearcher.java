package com.memorygraph.backend.memory.search;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.memorygraph.backend.ai.AiProperties;
import com.memorygraph.backend.ai.embedding.EmbeddingClient;
import com.memorygraph.backend.ai.embedding.MemoryEmbeddingStore;
import com.memorygraph.backend.memory.domain.MemoryType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ranks memories by cosine distance of their stored embedding to the query embedding.
 * <p>
 * Not a {@link MemorySearcher} on its own: without text there is nothing to embed, and with only
 * filters the full-text path already answers. This class is the semantic half of
 * {@link HybridMemorySearcher}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SemanticMemorySearcher {

    private static final String BASE_FILTERS = """
            where m.user_id = :userId
              and m.type in (:types)
              and m.occurred_at >= :from
              and m.occurred_at < :to
              and m.embedding is not null
              and m.embedding <=> cast(:queryVector as vector) < :maxDistance
            """;

    private static final String PERSON_FILTER = """
              and exists (
                select 1 from memory_people mp
                where mp.memory_id = m.id and mp.person_id = :personId
              )
            """;

    private static final String PLACE_FILTER = """
              and exists (
                select 1 from memory_places mpl
                where mpl.memory_id = m.id and mpl.place_id = :placeId
              )
            """;

    private final EntityManager entityManager;
    private final EmbeddingClient embeddings;
    private final AiProperties properties;

    /**
     * Returns the closest memories to {@code query}, ordered nearest-first. Empty when the query has
     * nothing to embed, no memory has an embedding yet, or nothing is within
     * {@link AiProperties#maxSemanticDistance()}.
     */
    public Page<SearchHit> search(SearchCriteria criteria, Pageable pageable) {
        if (!criteria.hasQuery()) {
            return Page.empty(pageable);
        }

        float[] queryVector;
        try {
            queryVector = embeddings.embed(criteria.query());
        } catch (IllegalArgumentException ex) {
            log.debug("Semantic search skipped: {}", ex.getMessage());
            return Page.empty(pageable);
        } catch (RuntimeException ex) {
            // Quota/network failures must not kill Ask — lexical retrieval still works.
            log.warn("Semantic search skipped (embedding provider error): {}", ex.getMessage());
            return Page.empty(pageable);
        }

        String vectorLiteral = MemoryEmbeddingStore.toVectorLiteral(queryVector);
        String filters = BASE_FILTERS;
        if (criteria.hasPerson()) {
            filters += PERSON_FILTER;
        }
        if (criteria.hasPlace()) {
            filters += PLACE_FILTER;
        }
        String source = "from memories m\n" + filters;

        Query page = bind(entityManager.createNativeQuery(
                "select m.id as id from memories m\n" + filters
                        + " order by m.embedding <=> cast(:queryVector as vector), m.occurred_at desc, m.id desc\n",
                Tuple.class), criteria, vectorLiteral)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Tuple> rows = page.getResultList();
        List<SearchHit> hits = rows.stream()
                .map(row -> new SearchHit((UUID) row.get("id"), null))
                .toList();

        return PageableExecutionUtils.getPage(hits, pageable, () -> count(criteria, source, vectorLiteral));
    }

    private long count(SearchCriteria criteria, String source, String vectorLiteral) {
        Number total = (Number) bind(entityManager.createNativeQuery("select count(*) " + source), criteria,
                vectorLiteral).getSingleResult();
        return total.longValue();
    }

    private Query bind(Query query, SearchCriteria criteria, String vectorLiteral) {
        query.setParameter("userId", criteria.userId())
                .setParameter("types", criteria.types().stream().map(MemoryType::name).toList())
                .setParameter("from", criteria.from())
                .setParameter("to", criteria.to())
                .setParameter("queryVector", vectorLiteral)
                .setParameter("maxDistance", properties.maxSemanticDistance());
        if (criteria.hasPerson()) {
            query.setParameter("personId", criteria.personId());
        }
        if (criteria.hasPlace()) {
            query.setParameter("placeId", criteria.placeId());
        }
        return query;
    }
}
