package com.memorygraph.backend.common.api;

import java.util.Map;

/**
 * Machine-readable failure detail. {@code code} is a stable identifier the frontend can branch on;
 * {@code message} is human readable and may change.
 */
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(code, message, fieldErrors);
    }
}
