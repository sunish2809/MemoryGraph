# 0013 — Photo enrichment: EXIF, OCR, captions, HEIC

Date: 2026-08-23

## Status

Accepted

## Context

Uploaded photos only had dimensions and a filename in searchable text. Phase 5 needs capture time,
location, text visible in the image, and optional descriptions — without making Compose or CI depend
on OpenAI or Tesseract.

## Decision

**Pipeline steps after `MEDIA_METADATA`, soft-fail optional stages, EXIF via metadata-extractor.**

- Job chain for uploads: `MEDIA_METADATA` → `OCR` → `CAPTION` → `EMBEDDING`. Only metadata owns
  `processing_status`. OCR/caption/embedding failures leave the memory usable.
- EXIF (including HEIC containers) is read with metadata-extractor. Capture time updates
  `occurred_at` unless the user supplied a time at upload (`occurred_at_locked`). GPS is stored on
  `media_assets`.
- HEIC is an accepted upload type (ISO BMFF `ftyp` brands). On ingest the backend converts HEIC/HEIF
  to JPEG via `heif-convert` (libheif in the Docker image) so the UI, captions and face detection
  can decode pixels. If conversion is unavailable, bytes are stored as HEIC and conversion is retried
  on metadata processing and first download.
- OCR uses Tess4J when native Tesseract is present; otherwise the step no-ops.
- Captions use an `ImageCaptionClient`: OpenAI vision when a `ChatModel` bean exists, otherwise a
  no-op. Same env pattern as Ask.

## Consequences

- A keyless, Tesseract-less deploy still completes enrichment and embeds filename-based text.
- Re-embedding after OCR/caption runs at the end of the caption step so semantic search sees the
  richest text available.
- Face recognition and people entities remain out of scope.

## Alternatives rejected

- **Requiring Tesseract in the Docker image.** Heavy and brittle for local Compose; soft-skip is
  enough until we dedicate an OCR worker image.
- **Blocking HEIC until we can decode pixels.** iPhone users would be locked out of storing the file
  at all; accepting bytes + EXIF is the better trade.
