package com.memorygraph.backend.memory.domain;

/**
 * The kind of enrichment a {@link ProcessingJob} performs. One value per pipeline step, so steps can
 * be retried and observed independently.
 */
public enum ProcessingJobType {

    /** Reads intrinsic properties of a stored file, such as image dimensions, and builds searchable text. */
    MEDIA_METADATA,

    /** Optical character recognition on photo pixels. Optional; skipped when Tesseract is unavailable. */
    OCR,

    /** Vision-model caption for a photo. Optional; skipped when no chat provider is configured. */
    CAPTION,

    /** Speech-to-text for audio/video. Optional; skipped when no OpenAI key is configured. */
    TRANSCRIPTION,

    /**
     * Turns the memory's searchable text into a vector for semantic retrieval. Runs after text is
     * available — immediately for notes, after {@link #CAPTION} / {@link #TRANSCRIPTION} for uploads —
     * and does not own {@code processing_status}: a memory is useful without an embedding.
     */
    EMBEDDING,

    /**
     * Detects faces on a photo via the local InsightFace sidecar, stores embeddings, and suggests
     * matches against already-named faces for this user.
     */
    FACE_DETECT
}
