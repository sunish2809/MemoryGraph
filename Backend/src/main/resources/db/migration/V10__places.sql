-- Phase 7: places clustered from photo GPS (reverse-geocode names can refine later).

CREATE TABLE places (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name     VARCHAR(255) NOT NULL,
    normalized_key   VARCHAR(64)  NOT NULL,
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_places_user_key ON places (user_id, normalized_key);
CREATE INDEX ix_places_user ON places (user_id, display_name);

CREATE TABLE memory_places (
    memory_id  UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    place_id   UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, place_id)
);

CREATE INDEX ix_memory_places_place ON memory_places (place_id);
