package com.memorygraph.backend.memory.application.imports;

import java.util.UUID;

/**
 * Thrown when an import is deleted (or otherwise dropped) while a worker is still writing memories.
 * Not a user-facing failure — the job row is already gone.
 */
public class ImportAbortedException extends RuntimeException {

    public ImportAbortedException(UUID importId) {
        super("Import " + importId + " was deleted while processing");
    }
}
