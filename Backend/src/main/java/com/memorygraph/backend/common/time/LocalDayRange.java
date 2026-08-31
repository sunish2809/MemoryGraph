package com.memorygraph.backend.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A range of calendar days, resolved into the instants a query can compare against.
 * <p>
 * Both the timeline and search let a caller ask for "1 March to 5 March" in their own zone, which is a
 * different span of instants for a reader in Kolkata than for one in Los Angeles. Resolving that in one
 * place keeps the two endpoints from disagreeing about which memories fall inside the same dates.
 *
 * @param from inclusive lower bound
 * @param to   exclusive upper bound, one day past the requested last day so the whole of it is covered
 */
public record LocalDayRange(Instant from, Instant to) {

    /**
     * Open-ended bounds, chosen to sit inside what a {@code timestamptz} can hold. Deliberately not
     * {@link Instant#EPOCH}: a scanned childhood photo from the 1960s is exactly the kind of memory this
     * product exists for, and clamping the lower bound to 1970 would quietly hide it.
     */
    public static final Instant FAR_PAST = Instant.parse("0001-01-01T00:00:00Z");
    public static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    /** A missing bound means "no limit in that direction" rather than "today". */
    public static LocalDayRange of(LocalDate from, LocalDate to, ZoneId zone) {
        return new LocalDayRange(
                from == null ? FAR_PAST : from.atStartOfDay(zone).toInstant(),
                to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** True when neither end was constrained, so a caller can skip a range query entirely. */
    public boolean isUnbounded() {
        return FAR_PAST.equals(from) && FAR_FUTURE.equals(to);
    }
}
