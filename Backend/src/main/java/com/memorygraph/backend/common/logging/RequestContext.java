package com.memorygraph.backend.common.logging;

public final class RequestContext {

    /** MDC key and response header used to correlate log lines with a single HTTP request. */
    public static final String REQUEST_ID_KEY = "requestId";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** MDC key for the authenticated user, so private-data access is always traceable. */
    public static final String USER_ID_KEY = "userId";

    private RequestContext() {
    }
}
