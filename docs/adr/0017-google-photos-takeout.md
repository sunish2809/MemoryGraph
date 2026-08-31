# 0017 — Google Photos via Takeout (not Library API sync)

Date: 2026-08-23

## Status

Accepted

## Context

Users want Google Photos in MemoryGraph. As of March 2025 Google removed third-party
`photoslibrary.readonly` access to a user's whole library. The Photos **Picker** API only supports
interactive selection, not bulk historical sync. Takeout remains the practical bulk path.

## Decision

**`POST /api/v1/imports/google-photos` accepts a Google Takeout `.zip` of Photos.**

- Async `import_jobs` with kind `GOOGLE_PHOTOS` (same runner pool as WhatsApp).
- Images become `PHOTO` memories (`source=IMPORT`); Takeout JSON sidecars supply capture time / GPS
  when present; otherwise EXIF enrichment fills gaps.
- Checksum idempotency on the zip bytes.
- Cap 5 000 photos per zip; upload size follows `MAX_FILE_SIZE` (1.5GB by default).

Live Picker OAuth is available as a second path ([ADR 0018](0018-google-photos-picker-and-geocode.md));
it does not replace Takeout for large archives.

## Consequences

- Users must export via [takeout.google.com](https://takeout.google.com/) (select Google Photos).
- Very large libraries may still need multiple Takeout chunks under the upload limit.
- No background continuous sync with Google Photos.

## Alternatives rejected

- **Library API full-library sync.** No longer permitted for third-party apps.
- **Picker-only.** Fine for a few picks; poor for “import my life”.
