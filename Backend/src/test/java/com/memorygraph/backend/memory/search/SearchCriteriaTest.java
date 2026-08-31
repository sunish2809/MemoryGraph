package com.memorygraph.backend.memory.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.memory.domain.MemoryType;

/**
 * The factory is where absent filters become their permissive form. Getting that wrong either hides
 * memories the caller did not intend to hide, or lets a missing type list mean "match nothing".
 */
class SearchCriteriaTest {

    private static final UUID USER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final LocalDayRange UNBOUNDED = LocalDayRange.of(null, null, ZoneOffset.UTC);

    @Test
    void treatsBlankQueryAsNoQuery() {
        SearchCriteria criteria = SearchCriteria.of(USER, "   ", List.of(), UNBOUNDED, SearchSort.NEWEST);

        assertThat(criteria.hasQuery()).isFalse();
        assertThat(criteria.query()).isNull();
    }

    @Test
    void keepsARealQuery() {
        SearchCriteria criteria = SearchCriteria.of(USER, " sikkim ", List.of(), UNBOUNDED, SearchSort.RELEVANCE);

        assertThat(criteria.hasQuery()).isTrue();
        assertThat(criteria.query()).isEqualTo("sikkim");
        assertThat(criteria.sort()).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void fallsBackToNewestWhenRelevanceIsAskedForWithoutAQuery() {
        SearchCriteria criteria = SearchCriteria.of(USER, null, List.of(), UNBOUNDED, SearchSort.RELEVANCE);

        assertThat(criteria.sort()).isEqualTo(SearchSort.NEWEST);
    }

    @Test
    void widensAnEmptyTypeListToEveryType() {
        SearchCriteria criteria = SearchCriteria.of(USER, null, List.of(), UNBOUNDED, SearchSort.NEWEST);

        assertThat(criteria.types()).isEqualTo(EnumSet.allOf(MemoryType.class));
    }

    @Test
    void keepsAnExplicitTypeFilter() {
        SearchCriteria criteria = SearchCriteria.of(USER, null, List.of(MemoryType.PHOTO, MemoryType.TEXT),
                UNBOUNDED, SearchSort.NEWEST);

        assertThat(criteria.types()).isEqualTo(EnumSet.of(MemoryType.PHOTO, MemoryType.TEXT));
    }

    @Test
    void rejectsConstructingWithNoTypes() {
        assertThatThrownBy(() -> new SearchCriteria(USER, null, Set.of(), LocalDayRange.FAR_PAST,
                LocalDayRange.FAR_FUTURE, SearchSort.NEWEST, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("types must not be empty");
    }

    @Test
    void copiesTheDateBoundsFromTheResolvedRange() {
        LocalDayRange range = LocalDayRange.of(LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31),
                ZoneOffset.UTC);

        SearchCriteria criteria = SearchCriteria.of(USER, "sikkim", List.of(MemoryType.TEXT), range,
                SearchSort.OLDEST);

        assertThat(criteria.from()).isEqualTo(range.from());
        assertThat(criteria.to()).isEqualTo(range.to());
        assertThat(criteria.sort()).isEqualTo(SearchSort.OLDEST);
        assertThat(criteria.userId()).isEqualTo(USER);
    }
}
