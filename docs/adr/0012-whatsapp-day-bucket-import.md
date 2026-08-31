# 0012 — WhatsApp export import as day-bucketed conversation memories

Date: 2026-08-23

## Status

Accepted

## Context

Phase 5 starts with WhatsApp because it is the richest text source most people already have. An
export is a long `_chat.txt` (optionally inside a zip with media). We need a normalisation that fits
the existing Memory model, stays searchable, and does not create one row per message.

## Decision

**Async `import_jobs` row, day-bucketed `CONVERSATION` memories, checksum idempotency.**

- `POST /api/v1/imports/whatsapp` stores the export and creates an `import_jobs` row. Parsing runs on
  the same worker pool as media enrichment, so a large chat does not block the HTTP thread.
- Messages are grouped by calendar day (as written in the export). Each day becomes one
  `Memory` of type `CONVERSATION` and source `IMPORT`, with a transcript in `content` and
  `occurred_at` from the first message of that day, interpreted in the caller-supplied IANA zone
  (WhatsApp timestamps have no zone).
- Media files present in a zip and referenced in the chat become separate `PHOTO` memories and enter
  the normal `MEDIA_METADATA → OCR → CAPTION → EMBEDDING` pipeline.
- Idempotency is SHA-256 of the chat plaintext: re-uploading the same export returns the existing
  job. A failed job can be retried; a completed job is not duplicated.
- Per-message `ConversationMessage` rows remain deferred. Day buckets are enough for timeline,
  search and Ask.

## Consequences

- Ask and search work on WhatsApp text without a new retrieval path.
- Very large exports may hit the upload size limit; exporting without media (`.txt` alone) is the
  escape hatch.
- Locale-specific date formats that we do not recognise yield zero messages and a failed import
  rather than silent garbage — the parser is extended as we meet real exports.

## Alternatives rejected

- **One memory per message.** Floods the timeline and embedding queue.
- **One memory for the whole chat.** Untargetable on the timeline and too large for a single vector.
- **Reuse `processing_jobs` for the import itself.** Those jobs require a `memory_id`; an import
  creates many memories.
