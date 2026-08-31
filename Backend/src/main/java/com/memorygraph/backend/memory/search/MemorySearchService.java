package com.memorygraph.backend.memory.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a search request into loaded, ordered results.
 * <p>
 * Split from {@code MemoryService} because it answers a different question. Creating and deleting
 * memories is about protecting invariants; search is about ranking and loading, and the only thing the
 * two share is the repository they read through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final MemorySearcher searcher;
    private final MemoryRepository memories;

    /**
     * Matching and loading are two steps on purpose. The searcher returns identifiers, which are then
     * hydrated with their media in a single fetch join; asking the ranked query itself to join media
     * would multiply rows per memory and make both the ranking and the page size wrong.
     */
    @Transactional(readOnly = true)
    public Page<SearchResult> search(SearchCriteria criteria, Pageable pageable) {
        Page<SearchHit> hits = searcher.search(criteria, pageable);
        if (hits.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> ids = hits.getContent().stream().map(SearchHit::memoryId).toList();
        Map<UUID, Memory> loaded = memories.findAllWithAssets(ids).stream()
                .collect(Collectors.toMap(Memory::getId, Function.identity()));

        // The repository returns its own order, so results are reassembled by walking the hits: rank is
        // the whole point of a search response and would otherwise be silently replaced by date order.
        List<SearchResult> results = new ArrayList<>(hits.getNumberOfElements());
        for (SearchHit hit : hits) {
            Memory memory = loaded.get(hit.memoryId());
            if (memory == null) {
                // Deleted between being matched and being loaded. Dropping it is better than failing the
                // whole search over one row that no longer exists.
                log.debug("Search hit {} disappeared before it could be loaded", hit.memoryId());
                continue;
            }
            results.add(new SearchResult(memory, hit.snippet()));
        }

        return new PageImpl<>(results, pageable, hits.getTotalElements());
    }
}
