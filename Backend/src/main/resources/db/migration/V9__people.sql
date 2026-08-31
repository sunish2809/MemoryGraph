-- Phase 6: people recognised from chat senders (face clustering later).

CREATE TABLE people (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name     VARCHAR(255) NOT NULL,
    normalized_name  VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_people_user_normalized ON people (user_id, normalized_name);
CREATE INDEX ix_people_user ON people (user_id, display_name);

CREATE TABLE memory_people (
    memory_id  UUID NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    person_id  UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, person_id)
);

CREATE INDEX ix_memory_people_person ON memory_people (person_id);
