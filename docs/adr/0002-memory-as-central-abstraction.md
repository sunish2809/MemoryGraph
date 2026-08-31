# 0002 — Memory is the central abstraction

Status: Accepted

## Context

It would be easy to build this as "chat with your documents": store files, chunk them, embed the
chunks, retrieve chunks. That design answers questions about *documents*. It cannot answer
"what happened on 15 August 2024?", because a chunk has no notion of when or where something
happened, or who was there.

The questions this product exists to answer are temporal, spatial and social.

## Decision

Model one domain entity — `Memory` — that every source normalises into. A photo, a WhatsApp thread,
a voice note and a typed note all become memories with the same shape, differing only in `type` and
in the enrichment attached to them.

Two field choices carry most of the weight:

- **`occurred_at`, not `created_at`, is the timeline axis.** A photo imported today may have
  happened in 2019. Ordering by import time would make the timeline meaningless, so the memory's own
  point in time is a mandatory, indexed column, and it is what every timeline and date filter uses.
- **`content` is normalised searchable text, whatever the source.** For a note it is the body; for a
  photo, the generated caption plus OCR text; for audio and video, the transcript. Full-text and
  semantic search read one column instead of branching per media type.

`type` and `source` are stored as `varchar` with no `CHECK` constraint, so a new importer adds an
enum constant without a migration.

## Consequences

- Retrieval can combine "when", "where", "who" and "about what" in one query, because they are all
  columns or joins on one table rather than properties of unrelated document types.
- Adding a source means writing an importer that produces `Memory` rows. The timeline, search and
  question answering pick it up with no changes.
- Everything hangs off one table, which will need care as it grows: partitioning by user or time is
  the expected escape hatch, and the existing indexes are already user-scoped.
- Some source-specific fidelity is lost in normalisation. Source-specific detail belongs in the
  satellite entities (`MediaAsset`, `ConversationMessage`) that reference the memory, not in wider
  and wider `Memory` rows.

## Alternatives rejected

- **A table per source type.** Every query becomes a union across a growing number of tables, and
  each new source touches every read path.
- **Chunks as the primary entity.** Good for document question answering, structurally unable to
  support a timeline.
