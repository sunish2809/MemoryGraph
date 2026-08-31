package com.memorygraph.backend.memory.domain;

/**
 * Kind of memory, which determines how it is processed and rendered.
 * <p>
 * Persisted as a string in a column with no CHECK constraint, so new kinds can be added without a
 * database migration.
 */
public enum MemoryType {

    TEXT,
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    CONVERSATION,
    EVENT
}
