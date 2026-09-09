-- Per-account monthly count of language-model Ask calls (not retrieval-only fallbacks).

CREATE TABLE ask_generation_usage (
    user_id      UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    period_start DATE    NOT NULL,
    used_count   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, period_start)
);
