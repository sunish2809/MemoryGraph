-- Password reset tokens and a clock used to reject JWTs issued before a password change.

ALTER TABLE users
    ADD COLUMN credentials_updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ '1970-01-01 00:00:00+00';

CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);
