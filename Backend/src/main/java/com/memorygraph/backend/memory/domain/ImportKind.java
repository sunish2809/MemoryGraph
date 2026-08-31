package com.memorygraph.backend.memory.domain;

/** Which bulk importer produced an {@link ImportJob}. */
public enum ImportKind {
    WHATSAPP,
    /** Google Takeout zip of Google Photos (Library API full-library sync is not available to apps). */
    GOOGLE_PHOTOS,
    /** Interactive Photos Picker selection (OAuth); not continuous sync. */
    GOOGLE_PHOTOS_PICKER
}
