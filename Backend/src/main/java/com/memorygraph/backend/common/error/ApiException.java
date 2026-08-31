package com.memorygraph.backend.common.error;

import lombok.Getter;

/**
 * Base class for failures that are part of the API contract. Anything that escapes as a plain
 * {@link RuntimeException} is treated as an unexpected server error and its detail is not exposed.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
