package com.memorygraph.backend.ai.rag;

import java.time.LocalDate;

/**
 * How many language-model Ask calls this account has left in the current UTC month.
 * {@code capped} is false when chat is off or the operator set the limit to 0 (unlimited).
 */
public record AskQuotaStatus(
        boolean capped,
        int limit,
        int used,
        int remaining,
        LocalDate periodStart,
        LocalDate resetsOn) {

    public static AskQuotaStatus unlimited() {
        return new AskQuotaStatus(false, 0, 0, 0, null, null);
    }

    public static AskQuotaStatus capped(int limit, int used, LocalDate periodStart, LocalDate resetsOn) {
        int remaining = Math.max(0, limit - used);
        return new AskQuotaStatus(true, limit, used, remaining, periodStart, resetsOn);
    }
}
