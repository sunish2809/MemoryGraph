-- Phase 6: per-message rows for conversation day-bucket memories (WhatsApp first).

CREATE TABLE conversation_messages (
    id           UUID         PRIMARY KEY,
    memory_id    UUID         NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    sent_at      TIMESTAMPTZ  NOT NULL,
    sender_name  VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    sort_index   INTEGER      NOT NULL,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_conversation_messages_memory ON conversation_messages (memory_id, sort_index);
CREATE INDEX ix_conversation_messages_user ON conversation_messages (user_id, sent_at DESC);
