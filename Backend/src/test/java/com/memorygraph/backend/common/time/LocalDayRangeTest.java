package com.memorygraph.backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LocalDayRangeTest {

    @Test
    void treatsMissingBoundsAsUnlimited() {
        LocalDayRange range = LocalDayRange.of(null, null, ZoneOffset.UTC);

        assertThat(range.from()).isEqualTo(LocalDayRange.FAR_PAST);
        assertThat(range.to()).isEqualTo(LocalDayRange.FAR_FUTURE);
        assertThat(range.isUnbounded()).isTrue();
    }

    @Test
    void makesTheLastDayInclusiveByExcludingTheNextMidnight() {
        LocalDayRange range = LocalDayRange.of(LocalDate.of(2019, 6, 1), LocalDate.of(2019, 6, 1), ZoneOffset.UTC);

        assertThat(range.from()).isEqualTo(Instant.parse("2019-06-01T00:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2019-06-02T00:00:00Z"));
        assertThat(range.isUnbounded()).isFalse();
    }

    /**
     * 20:00 UTC on 1 March is still 1 March in UTC and already 2 March in Kolkata. The same calendar
     * date therefore describes two different windows, which is why the zone is a required input.
     */
    @Test
    void resolvesTheSameCalendarDateToDifferentInstantsInDifferentZones() {
        LocalDate firstOfMarch = LocalDate.of(2024, 3, 1);
        Instant lateEveningUtc = Instant.parse("2024-03-01T20:00:00Z");

        LocalDayRange utc = LocalDayRange.of(firstOfMarch, firstOfMarch, ZoneOffset.UTC);
        LocalDayRange kolkata = LocalDayRange.of(firstOfMarch, firstOfMarch, ZoneId.of("Asia/Kolkata"));

        assertThat(lateEveningUtc).isAfterOrEqualTo(utc.from()).isBefore(utc.to());
        // 20:00 UTC is 01:30 on 2 March in Kolkata, so it sits after that day's window.
        assertThat(lateEveningUtc).isAfterOrEqualTo(kolkata.to());
    }
}
