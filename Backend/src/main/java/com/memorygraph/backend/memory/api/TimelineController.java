package com.memorygraph.backend.memory.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.memory.api.dto.TimelineResponse;
import com.memorygraph.backend.memory.application.MemoryService;
import com.memorygraph.backend.memory.domain.Memory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * The timeline: the same memories as {@code /memories}, but grouped into the days they happened on.
 * <p>
 * Kept as a separate endpoint rather than a flag on the list, because it answers a different question
 * ("what was my life like around then") and therefore has a different shape.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/timeline")
@Validated
@RequiredArgsConstructor
public class TimelineController {

    private static final int MAX_PAGE_SIZE = 200;

    private final MemoryService memoryService;

    /**
     * @param from inclusive first day, interpreted in {@code zone}; omit for no lower bound
     * @param to   inclusive last day, interpreted in {@code zone}; omit for no upper bound
     * @param zone IANA timezone the days are computed in. Defaults to UTC, but a client should send
     *             the viewer's actual zone, or memories recorded late at night land on the wrong day.
     */
    @GetMapping
    public ApiResponse<TimelineResponse> timeline(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "UTC") String zone,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        ZoneId zoneId = ViewerZone.parse(zone);
        LocalDayRange range = LocalDayRange.of(from, to, zoneId);
        UUID userId = CurrentUser.requireId();
        Pageable pageable = PageRequest.of(page, size);

        Page<Memory> memories = range.isUnbounded()
                ? memoryService.list(userId, pageable)
                : memoryService.listWindow(userId, range.from(), range.to(), pageable);

        return ApiResponse.success(TimelineResponse.from(memories, zoneId));
    }

    /** Deletes all memories that fall on this calendar day in the viewer zone. */
    @DeleteMapping("/days/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDay(
            @PathVariable LocalDate date,
            @RequestParam(defaultValue = "UTC") String zone) {
        memoryService.deleteDay(CurrentUser.requireId(), date, ViewerZone.parse(zone));
    }
}
