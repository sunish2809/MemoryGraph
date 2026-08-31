# 0014 — Conversation messages as a satellite of day-bucket memories

Date: 2026-08-23

## Status

Accepted

## Context

Phase 5 stored WhatsApp chats as one `Memory` per calendar day with a transcript in `content`. That
keeps search and Ask simple, but the detail page can only show a wall of text. Users expect a
chat-style view with senders and times.

## Decision

**Keep day-bucket `CONVERSATION` memories; add `conversation_messages` rows as a satellite.**

- Each imported day still creates one `Memory` with `content` set to the day transcript (full-text,
  embeddings, and Ask unchanged — [ADR 0002](0002-memory-as-central-abstraction.md) satellite
  pattern).
- WhatsApp import also persists one `conversation_messages` row per parsed message (`sent_at`,
  `sender_name`, `body`, `sort_index`), owned by the day memory.
- `GET /memories/{id}` returns `messages[]` ordered by `sort_index` when the type is `CONVERSATION`
  (empty for older imports without rows).
- The UI renders chat bubbles when `messages` is non-empty; otherwise it falls back to the transcript.

No backfill from `content` in this pass. Re-import is blocked by checksum, so message rows appear for
new imports (or after deleting the old job and memories).

## Consequences

- Detail fidelity without flooding the timeline or the embedding queue.
- Slightly larger imports (one row per message) — acceptable at personal-archive scale.
- Older imports remain readable via transcript until the user re-imports.

## Alternatives rejected

- **One memory per message.** Timeline and embedding noise (rejected in ADR 0012).
- **Parse bubbles from `content` on read.** Fragile and duplicates parser logic.
- **Replace `content` with messages only.** Would break search/Ask until a new retrieval path existed.
