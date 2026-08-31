package com.memorygraph.backend.storage;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

/**
 * The object store could not complete an operation. Surfaces as a server error: the caller did
 * nothing wrong, the storage backend did.
 */
public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }

    public StorageException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }
}
