-- Google Photos Picker OAuth tokens (per user) and place reverse-geocode tracking.

ALTER TABLE places
    ADD COLUMN geocoded_at TIMESTAMPTZ;

CREATE TABLE google_oauth_tokens (
    user_id       UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    access_token  TEXT         NOT NULL,
    refresh_token TEXT,
    expires_at    TIMESTAMPTZ  NOT NULL,
    scope         TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
