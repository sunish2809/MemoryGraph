package com.memorygraph.backend.common.time;

import java.time.DateTimeException;
import java.time.ZoneId;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

/**
 * The timezone a request wants its calendar days computed in.
 * <p>
 * Everything is stored as an instant, but "which day did this happen on" only has an answer relative
 * to a zone, and the only zone that matters is the viewer's. A memory recorded at 00:30 in Kolkata
 * belongs to that day for the person who lived it, not to the previous one because the server keeps
 * its clocks in UTC.
 */
public final class ViewerZone {

    private ViewerZone() {
    }

    /**
     * @throws ApiException with {@link ErrorCode#VALIDATION_FAILED} for anything that is not a known
     *                      IANA zone, because an unrecognised zone is a client mistake rather than a
     *                      reason to silently fall back to UTC and answer the wrong question.
     */
    public static ZoneId parse(String ianaId) {
        try {
            return ZoneId.of(ianaId);
        } catch (DateTimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown timezone: " + ianaId, ex);
        }
    }
}
