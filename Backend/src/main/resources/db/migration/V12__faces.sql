-- Phase 10: face detections with embeddings for free local InsightFace matching.

CREATE TABLE face_detections (
    id                    UUID PRIMARY KEY,
    memory_id             UUID         NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id              UUID         NOT NULL REFERENCES media_assets (id) ON DELETE CASCADE,
    x                     DOUBLE PRECISION NOT NULL,
    y                     DOUBLE PRECISION NOT NULL,
    width                 DOUBLE PRECISION NOT NULL,
    height                DOUBLE PRECISION NOT NULL,
    embedding             vector(512),
    person_id             UUID REFERENCES people (id) ON DELETE SET NULL,
    suggested_person_id   UUID REFERENCES people (id) ON DELETE SET NULL,
    confidence            DOUBLE PRECISION,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_face_detections_memory ON face_detections (memory_id);
CREATE INDEX ix_face_detections_user_unlabeled ON face_detections (user_id)
    WHERE person_id IS NULL;
CREATE INDEX ix_face_detections_user_named ON face_detections (user_id)
    WHERE person_id IS NOT NULL AND embedding IS NOT NULL;
