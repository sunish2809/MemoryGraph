package com.memorygraph.backend.memory.search;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.memorygraph.backend.memory.domain.MemoryType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

/**
 * Search over PostgreSQL's own full-text index.
 * <p>
 * Written as native SQL rather than JPQL because the whole feature is expressed in operators JPQL has
 * no vocabulary for: matching a {@code tsvector}, ranking with weighted fields, and extracting a
 * highlighted snippet. Wrapping those in Hibernate function registrations would hide what is really a
 * database capability behind a leaky abstraction.
 * <p>
 * Two statements rather than one with conditional clauses. A filter-only browse has no query to match,
 * nothing to rank and nothing to highlight, so bending the ranked statement to cover it would mean a
 * query whose behaviour changes depending on which parameters happen to be set.
 */
@Repository
@RequiredArgsConstructor
public class FullTextMemorySearcher implements MemorySearcher {

    /**
     * Bound once so the count and the page can never disagree about what qualifies, which is how a
     * paginated result ends up claiming a total it does not return.
     */
    private static final String BASE_FILTERS = """
            where m.user_id = :userId
              and m.type in (:types)
              and m.occurred_at >= :from
              and m.occurred_at < :to
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

    private static final String HEADLINE_OPTIONS = "'StartSel=\"" + SearchHighlight.START + "\""
            + ", StopSel=\"" + SearchHighlight.END + "\""
            + ", MaxFragments=2, MaxWords=22, MinWords=6, FragmentDelimiter=\" … \"'";

    private final EntityManager entityManager;

    @Override
    public Page<SearchHit> search(SearchCriteria criteria, Pageable pageable) {
        return search(criteria, pageable, false);
    }

    /**
     * Lexical half of Ask: OR of content words rather than AND, so "What happened on the Sikkim trip?"
     * can still find a note titled "Sikkim trip".
     */
    public Page<SearchHit> searchForAsk(SearchCriteria criteria, Pageable pageable) {
        return search(criteria, pageable, true);
    }

    private Page<SearchHit> search(SearchCriteria criteria, Pageable pageable, boolean forAsk) {
        String source = source(criteria, forAsk);

        Query page = bind(entityManager.createNativeQuery(select(criteria) + source + orderBy(criteria.sort()),
                Tuple.class), criteria)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Tuple> rows = page.getResultList();
        List<SearchHit> hits = rows.stream()
                .map(row -> new SearchHit((UUID) row.get("id"), (String) row.get("snippet")))
                .toList();

        return PageableExecutionUtils.getPage(hits, pageable, () -> count(criteria, source));
    }

    private String source(SearchCriteria criteria, boolean forAsk) {
        String filters = filters(criteria);
        if (!criteria.hasQuery()) {
            return "from memories m\n" + filters;
        }
        String queryFn = forAsk ? "memory_ask_query" : "memory_search_query";
        return "from memories m, " + queryFn + "(:query) as q(query)\n"
                + filters + "  and m.search_vector @@ q.query\n";
    }

    private static String filters(SearchCriteria criteria) {
        String filters = BASE_FILTERS;
        if (criteria.hasPerson()) {
            filters += PERSON_FILTER;
        }
        if (criteria.hasPlace()) {
            filters += PLACE_FILTER;
        }
        return filters;
    }

    private String select(SearchCriteria criteria) {
        if (!criteria.hasQuery()) {
            return "select m.id as id, cast(null as text) as snippet\n";
        }
        return "select m.id as id,\n"
                + "       ts_headline('english', concat_ws(' ', m.title, m.description, m.content), q.query, "
                + HEADLINE_OPTIONS + ") as snippet\n";
    }

    private String orderBy(SearchSort sort) {
        return switch (sort) {
            case RELEVANCE -> "order by ts_rank_cd(m.search_vector, q.query) desc, m.occurred_at desc, m.id desc";
            case NEWEST -> "order by m.occurred_at desc, m.id desc";
            case OLDEST -> "order by m.occurred_at asc, m.id asc";
        };
    }

    private long count(SearchCriteria criteria, String source) {
        Number total = (Number) bind(entityManager.createNativeQuery("select count(*) " + source), criteria)
                .getSingleResult();
        return total.longValue();
    }

    private Query bind(Query query, SearchCriteria criteria) {
        query.setParameter("userId", criteria.userId())
                .setParameter("types", criteria.types().stream().map(MemoryType::name).toList())
                .setParameter("from", criteria.from())
                .setParameter("to", criteria.to());

        if (criteria.hasQuery()) {
            query.setParameter("query", criteria.query());
        }
        if (criteria.hasPerson()) {
            query.setParameter("personId", criteria.personId());
        }
        if (criteria.hasPlace()) {
            query.setParameter("placeId", criteria.placeId());
        }
        return query;
    }
}
