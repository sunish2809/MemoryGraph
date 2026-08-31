-- Phase 2: the binary files behind a memory, and the asynchronous work that enriches them.

CREATE TABLE media_assets (
    id          UUID         PRIMARY KEY,
    memory_id   UUID         NOT NULL REFERENCES memories (id) ON DELETE CASCADE,

    -- Location in object storage. The bytes never live in PostgreSQL.
    storage_key VARCHAR(512) NOT NULL,

    -- The name the file had on the user's device: shown in the UI, never used as a path.
    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(127) NOT NULL,
    size_bytes  BIGINT       NOT NULL,

    -- Hex-encoded SHA-256 of the stored bytes.
    checksum    VARCHAR(64)  NOT NULL,

    -- Populated for images. Audio and video will add their own columns alongside the importers that
    -- can actually read them, rather than carrying always-null columns until then.
    width_px    INTEGER,
    height_px   INTEGER,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_media_assets_memory ON media_assets (memory_id);

-- One row per stored object: a duplicate key would mean two rows believing they own the same bytes,
-- so deleting one would break the other.
CREATE UNIQUE INDEX ux_media_assets_storage_key ON media_assets (storage_key);

-- Supports de-duplicating re-uploads of an identical file later.
CREATE INDEX ix_media_assets_checksum ON media_assets (checksum);

CREATE TABLE processing_jobs (
    id              UUID        PRIMARY KEY,
    memory_id       UUID        NOT NULL REFERENCES memories (id) ON DELETE CASCADE,

    -- Denormalised from the memory so jobs can be listed and audited per user without a join.
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    type            VARCHAR(48) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',

    attempts        INTEGER     NOT NULL DEFAULT 0,
    max_attempts    INTEGER     NOT NULL DEFAULT 3,

    -- Earliest time the job may be picked up; moves forward on each failed attempt (backoff).
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    error_message   TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The sweeper's only query: due work, cheapest possible scan, ignoring finished jobs entirely.
CREATE INDEX ix_processing_jobs_due ON processing_jobs (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX ix_processing_jobs_memory ON processing_jobs (memory_id, created_at);
CREATE INDEX ix_processing_jobs_user ON processing_jobs (user_id, created_at DESC);
