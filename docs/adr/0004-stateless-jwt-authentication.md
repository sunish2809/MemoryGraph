# 0004 — Stateless JWT authentication

Status: Accepted

## Context

The API is consumed by a single-page application and, later, possibly by import tools running
outside the browser. The data is highly private: a bug that lets one user read another's memories is
the worst thing this system can do.

## Decision

Authenticate with a short-lived, self-contained access token: `Authorization: Bearer <jwt>`, signed
with HMAC-SHA256 (Nimbus JOSE, the library Spring Security itself uses). Sessions are stateless and
CSRF protection is disabled, which is sound precisely because authentication is never an ambient
cookie.

Deliberate choices around it:

- **The account is re-read from the database on every request.** A token is proof of identity, not
  proof of standing. Without this, disabling an account would only take effect when the token
  happened to expire.
- **Authorization is deny-by-default.** `anyRequest().authenticated()`, with an explicit list of
  public endpoints (register, login, health).
- **Data isolation is enforced in the repository, not in services.** `MemoryRepository` exposes only
  owner-scoped finders; there is no `findById(id)` that ignores the owner, so a forgotten check
  cannot compile into a leak. An integration test asserts the invariant directly.
- **Login failures are indistinguishable.** An unknown email and a wrong password return the same
  `INVALID_CREDENTIALS` response, so the API cannot be used to enumerate accounts.
- **Password hashes are algorithm-prefixed** (Spring's delegating encoder, bcrypt today), so the
  algorithm can be upgraded without invalidating existing passwords.

## Consequences

- No session store, and horizontal scaling needs no shared state.
- There is no server-side revocation list. A stolen token is valid until it expires, which is why
  the TTL is short (one hour by default) and why the account is re-checked per request.
- The per-request user lookup is one indexed primary-key read. If it ever matters, it is cacheable.
- The token is held in `localStorage`, which is readable by any script on the page. Accepted for now
  given short TTLs and no third-party scripts; moving to a refresh token in an `httpOnly` cookie is
  the intended follow-up and is the right fix before this is exposed publicly.

## Alternatives rejected

- **Server-side sessions with cookies.** Immediate revocation, but requires a session store and CSRF
  protection, and does not serve non-browser clients well.
- **Long-lived tokens.** Removes the refresh problem and replaces it with a much larger blast radius
  on theft.
- **An external identity provider.** Reasonable later; unnecessary infrastructure for a
  self-hosted, single-user-per-account product, and it would put account data outside the user's own
  database.
