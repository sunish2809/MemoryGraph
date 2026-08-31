# 0018 — Google Photos Picker OAuth + reverse-geocoded place names

Date: 2026-08-23

## Status

Accepted

## Context

Takeout covers bulk history ([ADR 0017](0017-google-photos-takeout.md)). Users also want a live
“pick a few albums/photos” path. Google’s Photos Library API no longer allows third-party full-library
sync; the **Photos Picker API** is the supported interactive path. Separately, Places currently show
coordinate grid labels; users want names like “Gangtok”.

## Decision

1. **Photos Picker OAuth** when `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` are set:
   - Scope: `photospicker.mediaitems.readonly`
   - Flow: authorize → create picker session → user selects in Google UI → list/download →
     `ImportKind.GOOGLE_PHOTOS_PICKER` job (cap 500 items per selection)
   - Not continuous sync; each import is a fresh pick

2. **Reverse geocode** via Nominatim (OpenStreetMap) when linking GPS to places:
   - First successful resolve sets `display_name` (e.g. “Gangtok, Sikkim”) and `geocoded_at`
   - Failures keep the coordinate fallback; rate-limited ~1 req/s

## Consequences

- Operators must create a Google Cloud OAuth web client, enable Photos Picker API, and register the
  redirect URI (`GOOGLE_OAUTH_REDIRECT_URI`).
- Picker downloads use Google’s `=d` / `=dv` URLs (location EXIF is often stripped); place names still
  improve Takeout and other GPS sources.
- Nominatim requires a descriptive User-Agent; disable with `GEOCODING_ENABLED=false` if needed.

## Alternatives rejected

- **Library API continuous sync.** Blocked by Google policy for third-party apps.
- **Google Geocoding API only.** Nominatim is free for personal use and needs no key; Google can be
  added later if desired.
