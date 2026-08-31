# 7. Object storage is abstracted, and media never touches the database

Date: 2026-08-22

## Status

Accepted

## Context

Phase 2 introduces file uploads. Photos are the first, but videos, audio and documents follow, and a
single video can be gigabytes. We need somewhere to put bytes, and a way to hand them back to exactly
one person.

Three questions had to be answered together:

1. **Where do bytes live?** PostgreSQL can hold them, as `bytea` or large objects.
2. **What is the interface?** Local development, a self-hosted deployment and a managed one will not
   use the same backing store.
3. **How does a browser read a private photo?** An `<img src>` cannot carry an `Authorization`
   header.

## Decision

**Bytes live outside the database.** PostgreSQL holds only metadata — a `storage_key`, MIME type,
size, checksum and dimensions — pointing at an object in a store.

**One interface, `StorageService`**, with `store`, `retrieve`, `delete` and `exists`. The only
implementation today writes to the local filesystem, selected by
`memorygraph.storage.backend=LOCAL`. An S3 or MinIO implementation is a new class and a config value.

**The store owns its own layout.** Callers pass a `StorageKey`, built by the store as
`users/{userId}/memories/{memoryId}/{random}.{ext}`. A client-supplied filename never reaches the
filesystem: only a sanitised extension survives, so path traversal is not a matter of validation but
of construction. The original name is kept in the database for display.

**Media is streamed through the API**, at `GET /memories/{id}/media/{assetId}`, after an ownership
check against the database. The frontend fetches it as an authenticated blob and renders it from an
object URL.

## Consequences

### What this buys

Large files never inflate the database, its backups, or its replication stream. Restoring a database
dump stays a fast operation regardless of how many photos exist.

Swapping storage backends requires no change to any caller, so the local filesystem is a genuine
development convenience rather than a decision to unpick later.

Because every read is authorised per request, revoking access is immediate and no photo is ever
reachable by whoever holds a URL. For a product whose entire value rests on being trusted with
someone's private life, that is the right default.

The user prefix in the key means one person's entire media footprint can be exported or deleted by
prefix, which is what a data-export or account-deletion feature will need.

### What this costs

**Storage and the database can disagree.** An object is written before the transaction commits, so a
rollback can leave bytes with no row pointing at them, and a deletion removes rows before bytes, so a
storage failure can leave an unreferenced object. Both failure modes waste space and nothing else. The
alternative — committing metadata for bytes that may not have landed — produces a memory the user can
see but not open, which is worse. Orphans are recoverable by walking a user's prefix and comparing
against `media_assets`; that sweep is not built yet.

**Bytes pass through the application.** Every image view consumes a request thread and application
bandwidth. At personal-archive scale this is irrelevant, and it is the price of authorising each read.
When it stops being irrelevant, the fix is short-lived signed URLs issued *after* the same ownership
check, which slots in behind `StorageService` without changing the API contract. Deliberately not
built now: it adds key management and expiry semantics to solve a problem we do not have.

**The frontend cannot use a plain `<img src>`.** Images are fetched as blobs and turned into object
URLs, which must be revoked or the browser leaks memory as the user scrolls. That complexity is
contained in one component, `AuthenticatedImage`, and going through the query cache means a photo
shown in a list and then on its detail page is downloaded once.

**The local filesystem does not survive a container being rebuilt** unless the directory is a volume.
Compose mounts a named volume, and the directory is created in the image with the right ownership so
the non-root process can write to it. The volume is as precious as the database volume: losing it
loses the photos while leaving rows that point at them.

## Alternatives considered

**Bytes in PostgreSQL.** Transactionally consistent, and a single backup covers everything — genuinely
attractive for correctness. Rejected because a media archive grows without bound, and putting it in the
database makes every backup, restore and replica slower forever. Vacuum and WAL pressure from large
values are real operational costs.

**Filesystem access with no abstraction.** Less code today. Rejected because storage is the component
most likely to change when this moves off one machine, and retrofitting an interface once callers
depend on `Path` is far more invasive than starting with one.

**Public object URLs, with unguessable keys.** Simplest possible frontend. Rejected outright: security
by URL secrecy means a leaked link is permanent unrevokable access to someone's private photograph.
