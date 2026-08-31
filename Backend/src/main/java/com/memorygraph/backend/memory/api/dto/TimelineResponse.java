package com.memorygraph.backend.memory.api.dto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;

import com.memorygraph.backend.memory.domain.Memory;

/**
 * A page of the timeline, already grouped into days. Pagination is over memories rather than days, so
 * a page is always a predictable size no matter how unevenly a life is distributed.
 */
public record TimelineResponse(
        List<TimelineDayResponse> days,
        String zone,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {

    public static TimelineResponse from(Page<Memory> memories, ZoneId zone) {
        Map<LocalDate, List<MemorySummaryResponse>> byDay = new TreeMap<>(Comparator.reverseOrder());
        for (Memory memory : memories) {
            LocalDate day = LocalDate.ofInstant(memory.getOccurredAt(), zone);
            byDay.computeIfAbsent(day, key -> new ArrayList<>()).add(MemorySummaryResponse.from(memory));
        }

        List<TimelineDayResponse> days = byDay.entrySet().stream()
                .map(entry -> new TimelineDayResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();

        return new TimelineResponse(days, zone.getId(), memories.getNumber(), memories.getSize(),
                memories.getTotalElements(), memories.getTotalPages(), memories.hasNext());
    }
}
