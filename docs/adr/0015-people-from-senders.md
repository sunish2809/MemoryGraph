# 0015 — People from chat senders; face clustering deferred

Date: 2026-08-23

## Status

Accepted — face clustering shipped later in [ADR 0019](0019-free-local-faces.md).

## Context

“Everything about Rahul” needs a first-class person entity linked to memories. Face detection and
clustering are the long-term seam for photos, but shipping ML faces in this pass would pull in heavy
native deps and delay usable browse/filter for WhatsApp data we already have.

## Decision

**User-scoped `people` from WhatsApp senders, linked via `memory_people`. Face clustering later.**

- `people`: `display_name`, `normalized_name` (lower/stripped), unique per `(user_id, normalized_name)`.
- On WhatsApp day create: upsert a person per distinct sender; link the conversation memory (and photo
  memories from that day’s attachments when the sender is known).
- WhatsApp’s `You` maps to the account `displayName` so self is one stable person across chats.
- API: `GET /people`, `GET /people/{id}` (with recent memories); search accepts optional `personId`;
  dashboard stats include `totalPeople`.
- Schema notes the future face seam (same `people` rows can gain face clusters later) without
  OpenCV/InsightFace in this phase. Local InsightFace + confirm UI is [ADR 0019](0019-free-local-faces.md).

## Consequences

- Dashboard People is a real count; Ask/search can be narrowed to a person.
- Name collisions (“Rahul” in two chats) merge — acceptable until richer identity (phone, face).
- Photos without a chat sender stay unlinked until faces or manual tagging.

## Alternatives rejected

- **ML face clustering now.** Correct long-term, wrong for this delivery slice (superseded by 0019).
- **People only as free-text tags on memories.** No stable identity, no counts, no filter API.
- **Skip “You” entirely.** Loses the owner as a browsable person across imported chats.
