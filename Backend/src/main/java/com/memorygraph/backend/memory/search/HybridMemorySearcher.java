package com.memorygraph.backend.memory.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Combines lexical and semantic ranking by reciprocal rank fusion.
 * <p>
 * The two engines do not share a score scale — {@code ts_rank_cd} and cosine distance mean different
 * things — so they are fused by position alone: a memory that ranks well in either list rises, and a
 * memory that ranks well in both rises further. That is also how a third strategy can join later
 * without renegotiating scores.
 * <p>
 * Marked {@code @Primary} so {@link MemorySearchService} receives this rather than the full-text
 * searcher alone.
 */
@Primary
@Repository
@RequiredArgsConstructor
public class HybridMemorySearcher implements MemorySearcher {

    /**
     * Standard RRF constant. Higher values flatten the contribution of top ranks; 60 is the value
     * introduced with the original RRF paper and works well as a default.
     */
    private static final int RRF_K = 60;

    /**
     * How many candidates to pull from each engine before fusing. Large enough that a strong semantic
     * hit outside the final page is not discarded before fusion; small enough to stay cheap.
     */
    private static final int CANDIDATE_POOL = 50;

    private final FullTextMemorySearcher lexical;
    private final SemanticMemorySearcher semantic;

    @Override
    public Page<SearchHit> search(SearchCriteria criteria, Pageable pageable) {
        if (!criteria.hasQuery()) {
            return lexical.search(criteria, pageable);
        }

        // Newest / oldest asked for explicitly: honour that with the lexical path, which already
        // orders by date. Fusing would reintroduce relevance and contradict the request.
        if (criteria.sort() == SearchSort.NEWEST || criteria.sort() == SearchSort.OLDEST) {
            return lexical.search(criteria, pageable);
        }

        Pageable pool = PageRequest.of(0, CANDIDATE_POOL);
        Page<SearchHit> lexicalPage = lexical.search(criteria, pool);
        if (lexicalPage.isEmpty()) {
            // Lexical found nothing. Do not fall back to "nearest embedding" alone: without a
            // similarity ceiling that is trustworthy for every provider, that path turns stop words,
            // failed phrase queries and typos into false hits. Ask has its own retriever when a
            // paraphrase with no shared words needs to surface.
            return Page.empty(pageable);
        }

        Page<SearchHit> semanticPage = semantic.search(criteria, pool);
        if (semanticPage.isEmpty()) {
            return slice(lexicalPage.getContent(), pageable, lexicalPage.getTotalElements());
        }

        List<SearchHit> fused = fuse(lexicalPage.getContent(), semanticPage.getContent());
        return slice(fused, pageable, lexicalPage.getTotalElements());
    }

    /**
     * Walks both ranked lists and accumulates {@code 1 / (k + rank)}. Snippets come from the lexical
     * hit when present: only the text engine knows which words matched. Equal RRF scores keep the
     * lexical order, so a title match that full-text ranked first is not overturned by a coin-flip
     * when semantic ranks the same two memories the other way around.
     */
    public static List<SearchHit> fuse(List<SearchHit> lexicalHits, List<SearchHit> semanticHits) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Integer> lexicalRank = new HashMap<>();
        Map<UUID, String> snippets = new LinkedHashMap<>();

        for (int i = 0; i < lexicalHits.size(); i++) {
            SearchHit hit = lexicalHits.get(i);
            scores.merge(hit.memoryId(), 1.0 / (RRF_K + i + 1), Double::sum);
            lexicalRank.put(hit.memoryId(), i);
            if (hit.snippet() != null) {
                snippets.put(hit.memoryId(), hit.snippet());
            }
        }
        for (int i = 0; i < semanticHits.size(); i++) {
            SearchHit hit = semanticHits.get(i);
            scores.merge(hit.memoryId(), 1.0 / (RRF_K + i + 1), Double::sum);
            snippets.putIfAbsent(hit.memoryId(), hit.snippet());
        }

        List<UUID> ordered = new ArrayList<>(scores.keySet());
        ordered.sort(Comparator
                .<UUID>comparingDouble(id -> scores.getOrDefault(id, 0.0)).reversed()
                .thenComparingInt(id -> lexicalRank.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparing(UUID::compareTo));

        List<SearchHit> fused = new ArrayList<>(ordered.size());
        for (UUID id : ordered) {
            fused.add(new SearchHit(id, snippets.get(id)));
        }
        return fused;
    }

    private static Page<SearchHit> slice(List<SearchHit> all, Pageable pageable, long total) {
        int from = (int) pageable.getOffset();
        if (from >= all.size()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, total);
    }
}
