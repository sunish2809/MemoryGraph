# 0001 — Modular monolith, not microservices

Status: Accepted

## Context

The product has several plausibly separable concerns: ingestion, media processing, embedding
generation, retrieval and question answering. Some of them (video transcoding, transcription,
embedding) are CPU-heavy and will eventually want to scale independently of the API.

There is currently one developer, no traffic, and an unproven domain model.

## Decision

Build a single deployable Spring Boot application, internally divided into feature modules
(`auth`, `user`, `memory`, `health`, with `common` for cross-cutting concerns). Each module owns its
own API, application services, domain and persistence.

The rules that keep extraction cheap:

- A module never queries another module's tables or injects another module's repositories. It calls
  the owning module's application service.
- Every external integration sits behind an interface owned by the module that needs it
  (object storage, embedding provider, AI provider, transcription).
- Asynchronous work is modelled as an explicit job with a persisted status, not as an in-process
  callback, so the worker can move out of the process without changing the contract.

## Consequences

- One build, one deployment, one database, one transaction boundary. Local development is
  `docker compose up`.
- Refactoring across module boundaries is a compiler-checked rename rather than a coordinated
  release of several services.
- Nothing scales independently yet. When media processing genuinely needs its own scaling, it is
  extracted along the boundary that already exists.
- Module boundaries are a convention, not enforced by the build. They have to be maintained in code
  review; if they start eroding, adding a module-boundary test is the response.

## Alternatives rejected

- **Microservices from day one.** Distributed transactions, network failure modes and deployment
  overhead in exchange for scaling we do not need and boundaries we cannot yet place correctly.
- **A single package with no internal structure.** Cheapest today, but there would be no seam to
  extract along later, which is precisely what this project expects to need.
