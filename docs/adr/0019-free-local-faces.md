# 0019 — Free local face recognition (InsightFace sidecar)

Date: 2026-08-24

## Status

Accepted

## Context

Photos without WhatsApp senders stayed unlinked (ADR 0015 deferred ML faces). The personal memory
graph needs a way to say “this face is Raj / Aditya” without a paid cloud Face API, while keeping
bytes on the owner’s machine.

## Decision

**Local InsightFace (`buffalo_s`) FastAPI sidecar + confirm UI; reuse `people` / `memory_people`.**

- Compose service `faces` exposes `POST /detect` (normalised boxes + 512-d embedding) and `/health`.
- Backend job `FACE_DETECT` runs after photo caption; stores `face_detections` with optional
  `suggested_person_id` from nearest named embedding (cosine distance ≤ threshold).
- Manual `POST /memories/{id}/people` tags whole memories without ML.
- Confirming a face sets `person_id`, links `memory_people`, and seeds future suggestions.
- If the sidecar is down, detection no-ops; tagging still works.

## Consequences

- First model download needs disk + ~2–4 GB RAM for the faces container.
- Suggestions require at least one confirmed face (or tagged exemplar) per person.
- Face boxes assume the displayed image fills its element (no letterboxed `object-contain`).

## Alternatives rejected

- **Paid cloud Face APIs.** Cost and privacy mismatch for a personal archive.
- **Silent auto-tag with no confirm.** Too many false positives for identity.
- **One memory per face.** Timeline noise; memory stays the photo, faces are satellites.
