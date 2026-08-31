# 0006 — A single API response envelope

Status: Accepted

## Context

Clients need to distinguish "the request succeeded", "you sent something invalid", "your session
expired" and "the server broke" — and to do so uniformly, including for failures raised by the
security filter chain before any controller runs. Without a convention, each endpoint invents its
own error shape and the frontend accumulates special cases.

## Decision

Every response, success or failure, uses one envelope:

```json
{ "success": true,  "data": { … },                   "timestamp": "…", "requestId": "…" }
{ "success": false, "error": { "code", "message", "fieldErrors" }, "timestamp": "…", "requestId": "…" }
```

- `error.code` is a stable enum-like string that clients may branch on; `message` is for humans and
  may be reworded freely. `fieldErrors` maps a request field to its validation message, which the
  frontend renders inline on the matching input.
- A single `ErrorCode` enum is the only place an application error is mapped to an HTTP status.
- One `@RestControllerAdvice` translates every exception. Unexpected exceptions are logged with their
  stack trace and reported as a generic `INTERNAL_ERROR`, so internal detail never reaches a client.
- Spring Security's entry point and access-denied handler delegate to the MVC exception resolver, so
  a 401 from the filter chain has exactly the same shape as a 404 from a controller.
- Each request gets an id, echoed in the `X-Request-Id` response header and in the envelope, and
  attached to every log line for that request. A user reporting a failure hands over one id that
  locates the server-side story.

## Consequences

- The frontend has one `ApiRequestError` type and one place that unwraps responses; components deal
  with payloads and typed error codes rather than HTTP mechanics.
- Errors are debuggable across the boundary without exposing internals.
- The envelope costs a small amount of nesting and payload size, and every endpoint must return
  `ApiResponse<T>` rather than a bare DTO.
- The API is not directly HATEOAS- or problem-details-shaped, which would matter for a public API
  with third-party consumers.

## Alternatives rejected

- **Bare DTOs with HTTP status codes only.** No room for a stable error code or field errors, and
  status codes alone cannot distinguish "email already registered" from other conflicts.
- **RFC 9457 problem details.** A good standard, but success and failure would then have different
  shapes, and there would be no single place to carry the request id.
