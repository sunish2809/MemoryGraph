package com.memorygraph.backend.common.api;

import java.time.Instant;

import org.slf4j.MDC;

import com.memorygraph.backend.common.logging.RequestContext;

/**
 * Envelope returned by every endpoint so clients can rely on one response shape.
 * Exactly one of {@code data} / {@code error} is populated.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp, String requestId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), currentRequestId());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now(), currentRequestId());
    }

    private static String currentRequestId() {
        return MDC.get(RequestContext.REQUEST_ID_KEY);
    }
}
