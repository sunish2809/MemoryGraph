package com.memorygraph.backend.memory.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One calendar day of the user's life, in the timezone the client asked for.
 * <p>
 * Grouping happens server-side because "which day did this happen on" is a question about the viewer's
 * timezone, not about UTC, and getting it wrong puts late-evening memories on the wrong day.
 */
public record TimelineDayResponse(LocalDate date, List<MemorySummaryResponse> memories) {
}
