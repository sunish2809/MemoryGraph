package com.memorygraph.backend.auth.security;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

/**
 * Raised when a bearer token is absent, malformed, expired or not signed by this application.
 * The message is for logs only; the client always sees a generic 401.
 */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHENTICATED, message, cause);
    }
}
