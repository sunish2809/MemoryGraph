package com.memorygraph.backend.common.api;

/**
 * Single source of truth for URL versioning. Referenced from controllers as a compile-time
 * constant so the version can be changed in one place without string duplication.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";

    private ApiPaths() {
    }
}
