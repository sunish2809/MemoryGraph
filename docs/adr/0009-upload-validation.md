# 9. Uploads are identified by their bytes, not by what the client claims

Date: 2026-08-22

## Status

Accepted

## Context

The upload endpoint accepts a file from a browser and stores it, then serves it back later. Both halves
are dangerous if the file is not what it says it is.

A multipart upload arrives with a `Content-Type` and a filename, both chosen by whoever made the
request. Neither is evidence of anything. The concrete risks:

- A script or HTML file stored as `photo.png` and served back with `Content-Type: image/png`, or worse,
  sniffed by the browser as HTML and executed on our origin — which would hand an attacker the session
  of anyone who opened it.
- A filename like `../../../etc/passwd` or one containing control characters.
- A file large enough to exhaust disk, or one whose declared length understates its real size.

## Decision

**Type is determined from the file's own leading bytes.** A `SupportedMediaType` enum pairs each
accepted type with its magic-byte signature: JPEG, PNG, GIF and WebP. A file matching none of them is
rejected with `415`. The declared content type is used only to log a mismatch, never to decide.

**Signatures are hand-written rather than delegated to an image decoder.** `ImageIO` could confirm a
file parses, but a standard JDK has no WebP reader, so requiring it to parse would reject valid files.

**The allowlist is images only.** Video, audio and documents are rejected until the phase that can
actually process them exists.

**Size is checked twice**: once against the declared length for a fast, clear error, and again while
streaming to disk, because the declared length is a claim. Both sit behind the servlet container's own
limit and nginx's `client_max_body_size`.

**The client filename never reaches the filesystem.** The storage key is generated, keeping only a
sanitised extension. The original name is stored for display, stripped of any directory component and
control characters.

**Writes are atomic.** Content goes to a temporary file and is moved into place, so a rejection
part-way through cannot leave a truncated object that looks complete.

**Responses carry `X-Content-Type-Options: nosniff`**, so even if something unexpected were stored, a
browser would not reinterpret it as HTML.

## Consequences

### What this buys

Storing an executable payload as an image requires forging a real image signature, and the response
headers prevent the browser from reinterpreting it regardless. Traversal is not defended against — it
is structurally impossible, because no client-supplied string is ever part of a path.

An oversized upload is rejected before it can fill the disk, whether or not the client lied about its
size, and nothing partial is left behind.

The mapping from file type to memory type lives in one enum, so widening support in Phase 5 is one
constant per type.

### What this costs

**The allowlist is a closed door.** A user with an HEIC photo from an iPhone — a very likely case — gets
a rejection. That is the correct trade for now, since we could store HEIC but not read anything from
it, and silently accepting files we cannot process would be worse than declining them clearly. HEIC
support belongs with the extraction work in Phase 5.

**The first bytes of every upload are read twice**, once to sniff and once to store. Negligible, since
the sniff reads 16 bytes.

**Magic-byte matching is shallow.** It confirms a plausible header, not a well-formed image. A file
with a valid PNG header and corrupt data will be stored and then fail during enrichment — which is
handled, ending as a `FAILED` job and an "Incomplete" badge rather than a lost upload. Deeper validation
would mean fully decoding every upload, which is expensive and still not a guarantee.

**A correct file with an unrecognised signature is rejected outright.** Fewer false negatives would
require a full content-detection library such as Apache Tika; that is a large dependency to add for four
image formats, and can be revisited when the allowlist grows.

## Alternatives considered

**Trust the browser's `Content-Type`.** Zero code. Rejected: it is attacker-controlled, so it provides
no security property at all.

**Apache Tika for content detection.** Thorough, handles hundreds of formats. Rejected for now as
disproportionate — a significant dependency and its transitive tree to identify four formats. Worth
revisiting when documents and video are accepted.

**Store anything, validate on read.** Would accept HEIC and everything else today. Rejected because it
moves the risk to the point where bytes are served to a browser, which is exactly where it must not be,
and leaves the archive full of files nothing can open.
