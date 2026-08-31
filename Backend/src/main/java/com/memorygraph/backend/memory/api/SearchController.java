package com.memorygraph.backend.memory.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.api.PageResponse;
import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.memory.api.dto.SearchResultResponse;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.search.MemorySearchService;
import com.memorygraph.backend.memory.search.SearchCriteria;
import com.memorygraph.backend.memory.search.SearchSort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Search: the point of the whole product, which is to be able to find something you half remember.
 * <p>
 * Text and filters are one endpoint rather than two, because they are one question. "Photos from that
 * trip, some time in 2019" is a phrase, two type filters and a date range at once, and splitting them
 * across a search endpoint and a browse endpoint would make the client stitch two result sets together.
 * With no text supplied this degrades cleanly into a filtered browse.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/search")
@Validated
@RequiredArgsConstructor
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Long enough for a pasted sentence, short enough that nobody is doing anything else with it. The
     * text is safe at any length, but there is no legitimate query beyond this.
     */
    private static final int MAX_QUERY_LENGTH = 500;

    private final MemorySearchService searchService;

    /**
     * @param q     what to look for. Unquoted words match by prefix, so partial words find results while
     *              the user is still typing; anything quoted is matched as an exact phrase. Omit it to
     *              browse by filters alone.
     * @param type  repeatable, as {@code type=PHOTO&type=TEXT}. Omitted means every type.
     * @param from  inclusive first day, interpreted in {@code zone}
     * @param to    inclusive last day, interpreted in {@code zone}
     * @param zone  IANA timezone the date filters are resolved in, so "March 1st" means the viewer's
     *              March 1st
     * @param personId optional; when set, only memories linked to that person via {@code memory_people}
     * @param sort  defaults to relevance, which falls back to newest-first when there is no text to rank
     */
    @GetMapping
    public ApiResponse<PageResponse<SearchResultResponse>> search(
            @RequestParam(name = "q", required = false) @Size(max = MAX_QUERY_LENGTH) String q,
            @RequestParam(name = "type", required = false) List<MemoryType> type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "UTC") String zone,
            @RequestParam(defaultValue = "RELEVANCE") SearchSort sort,
            @RequestParam(required = false) UUID personId,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        ZoneId zoneId = ViewerZone.parse(zone);
        SearchCriteria criteria = SearchCriteria.of(CurrentUser.requireId(), q, type,
                LocalDayRange.of(from, to, zoneId), sort, personId, placeId);
        Pageable pageable = PageRequest.of(page, size);

        return ApiResponse.success(
                PageResponse.of(searchService.search(criteria, pageable), SearchResultResponse::from));
    }
}
