-- Phase 5: bulk import runs (WhatsApp first). One row per uploaded export; memories are created
-- asynchronously and linked only by ownership. Idempotency is (user_id, checksum of chat text).

CREATE TABLE import_jobs (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    kind              VARCHAR(32)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,

    storage_key       VARCHAR(512) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    -- SHA-256 of the chat plaintext (not the zip wrapper), so re-uploading the same export is a no-op.
    checksum          VARCHAR(64)  NOT NULL,
    zone              VARCHAR(64)  NOT NULL,
    chat_name         VARCHAR(255),

    memories_created  INTEGER      NOT NULL DEFAULT 0,
    error_message     TEXT,

    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_import_jobs_user_checksum ON import_jobs (user_id, checksum);
CREATE UNIQUE INDEX ux_import_jobs_storage_key ON import_jobs (storage_key);

CREATE INDEX ix_import_jobs_user ON import_jobs (user_id, created_at DESC);
CREATE INDEX ix_import_jobs_due ON import_jobs (created_at)
    WHERE status IN ('PENDING', 'FAILED');
