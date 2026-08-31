# 0016 — Audio/video transcription, Places from GPS, people graph UX

Date: 2026-08-23

## Status

Accepted

## Context

Phase 6 delivered chat fidelity and people-from-senders. The product north star still needs
speech-searchable audio/video, place facets from photo GPS, and a richer graph view. Full ML face
clustering remains deferred (ADR 0015).

## Decision

**Transcription job + Places entity + people co-occurrence graph; faces still deferred.**

1. **Audio/video upload** — magic-byte allowlist expands to MP4/MOV/WebM and MP3/WAV/M4A. Pipeline:
   `MEDIA_METADATA` → `TRANSCRIPTION` → `EMBEDDING` (photos keep OCR → CAPTION → EMBEDDING).
2. **Transcription** — OpenAI Whisper when a chat/OpenAI key is configured; otherwise soft no-op
   (filename still searchable). Transcript appends to `memory.content`.
3. **Places** — `places` + `memory_places`. On EXIF GPS, upsert a ~1 km grid cell and link the memory.
   Display names start as coordinates; reverse-geocode can refine later.
4. **Graph UX** — `GET /people/graph` returns people nodes and edges (shared memory counts). Frontend
   renders an interactive SVG. Face detection/clustering is still out of scope.

## Consequences

- Ask/search can answer from spoken content when Whisper is configured.
- Dashboard Places count becomes real for GPS photos.
- Graph is useful without faces; edges only exist when people co-occur on the same memory.

## Alternatives rejected

- **Local Whisper / ffmpeg in-process.** Heavier ops; HTTP Whisper reuses the existing OpenAI gate.
- **Reverse-geocode on every photo.** Needs an external maps API and rate limits; grid cells first.
- **ML faces in this slice.** Same deferral as ADR 0015.
