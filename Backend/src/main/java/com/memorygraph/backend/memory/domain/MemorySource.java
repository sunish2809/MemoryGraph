package com.memorygraph.backend.memory.domain;

/**
 * Where a memory came from. Grows as importers are added (WhatsApp, email, calendar, ...).
 */
public enum MemorySource {

    /** Typed directly into the app by the user. */
    MANUAL,

    /** Created from a file the user uploaded. */
    UPLOAD,

    /** Created by a bulk importer from an external export. */
    IMPORT
}
