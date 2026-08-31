-- Manual place names (so Nominatim does not overwrite "Gangtok"), extra GPS cells after a merge,
-- and trips as a named stretch of days.

ALTER TABLE places
    ADD COLUMN name_locked BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE place_grid_aliases (
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    normalized_key  VARCHAR(64) NOT NULL,
    place_id        UUID        NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, normalized_key)
);

CREATE INDEX ix_place_grid_aliases_place ON place_grid_aliases (place_id);

CREATE TABLE trips (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ  NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trips_range CHECK (ended_at >= started_at)
);

CREATE INDEX ix_trips_user_started ON trips (user_id, started_at DESC);
