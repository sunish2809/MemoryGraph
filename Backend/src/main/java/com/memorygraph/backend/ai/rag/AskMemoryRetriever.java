package com.memorygraph.backend.ai.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.ai.AiProperties;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.search.FullTextMemorySearcher;
import com.memorygraph.backend.memory.search.SearchCriteria;
import com.memorygraph.backend.memory.search.SearchHit;
import com.memorygraph.backend.memory.search.SearchSort;
import com.memorygraph.backend.memory.search.SemanticMemorySearcher;

import lombok.RequiredArgsConstructor;

/**
 * Retrieves memories for Ask.
 * <p>
 * Unlike the Search page — which refuses to return a hit with no lexical match — Ask may surface a
 * memory that only matched by meaning. That is the point of a paraphrase ("the Himalayan holiday"
 * → Sikkim trip). Lexical Ask uses OR of content words so filler verbs do not veto a hit. Results
 * are fused when both engines contribute; semantic-only results are kept when they clear the
 * distance ceiling.
 */
@Service
@RequiredArgsConstructor
public class AskMemoryRetriever {

    private final FullTextMemorySearcher lexical;
    private final SemanticMemorySearcher semantic;
    private final MemoryRepository memories;
    private final AiProperties properties;

    @Transactional(readOnly = true)
    public List<Memory> retrieve(SearchCriteria criteria) {
        var pool = PageRequest.of(0, properties.askTopK());
        // Ask always wants relevance ordering for fusion, even if the caller asked for newest.
        SearchCriteria ranked = new SearchCriteria(criteria.userId(), criteria.query(), criteria.types(),
                criteria.from(), criteria.to(), SearchSort.RELEVANCE, criteria.personId(), criteria.placeId());

        List<SearchHit> lexicalHits = lexical.searchForAsk(ranked, pool).getContent();
        List<SearchHit> semanticHits = semantic.search(ranked, pool).getContent();

        List<UUID> orderedIds;
        if (lexicalHits.isEmpty()) {
            orderedIds = semanticHits.stream().map(SearchHit::memoryId).toList();
        } else if (semanticHits.isEmpty()) {
            orderedIds = lexicalHits.stream().map(SearchHit::memoryId).toList();
        } else {
            orderedIds = com.memorygraph.backend.memory.search.HybridMemorySearcher
                    .fuse(lexicalHits, semanticHits)
                    .stream()
                    .map(SearchHit::memoryId)
                    .limit(properties.askTopK())
                    .toList();
        }

        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Memory> loaded = new LinkedHashMap<>();
        for (Memory memory : memories.findAllWithAssets(orderedIds)) {
            loaded.put(memory.getId(), memory);
        }

        List<Memory> result = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            Memory memory = loaded.get(id);
            if (memory != null) {
                result.add(memory);
            }
        }
        return result;
    }
}
