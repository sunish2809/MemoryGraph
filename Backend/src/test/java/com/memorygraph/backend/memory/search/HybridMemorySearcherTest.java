package com.memorygraph.backend.memory.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HybridMemorySearcherTest {

    @Test
    void prefersAMemoryThatRanksWellInBothLists() {
        UUID both = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID lexicalOnly = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID semanticOnly = UUID.fromString("33333333-3333-3333-3333-333333333333");

        List<SearchHit> lexical = List.of(
                new SearchHit(both, "[[sikkim]] from lexical"),
                new SearchHit(lexicalOnly, "only lexical"));
        List<SearchHit> semantic = List.of(
                new SearchHit(semanticOnly, null),
                new SearchHit(both, null));

        List<SearchHit> fused = HybridMemorySearcher.fuse(lexical, semantic);

        // both: 1st lexical + 2nd semantic — clear winner
        // semanticOnly / lexicalOnly: single-list ranks; lexical tie-break puts lexicalOnly ahead of
        // a semantic-only hit when RRF scores are otherwise comparable... actually semanticOnly is
        // 1st in semantic (1/61≈0.0164), lexicalOnly is 2nd in lexical (1/62≈0.0161), so semanticOnly
        // still edges it. Lexical order only breaks true ties.
        assertThat(fused).extracting(SearchHit::memoryId)
                .containsExactly(both, semanticOnly, lexicalOnly);
        assertThat(fused.getFirst().snippet()).isEqualTo("[[sikkim]] from lexical");
    }

    @Test
    void keepsLexicalSnippetWhenSemanticAlsoHits() {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        List<SearchHit> fused = HybridMemorySearcher.fuse(
                List.of(new SearchHit(id, "snippet")),
                List.of(new SearchHit(id, null)));

        assertThat(fused).hasSize(1);
        assertThat(fused.getFirst().snippet()).isEqualTo("snippet");
    }
}
