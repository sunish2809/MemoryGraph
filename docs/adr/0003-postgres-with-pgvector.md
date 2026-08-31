# 0003 — PostgreSQL with pgvector for hybrid retrieval

Status: Accepted

## Context

Answering "find photos from Kolkata where I was with Rahul" requires three things at once: metadata
filtering (place, people, date), lexical matching, and semantic similarity. The retrieval layer must
do this itself — handing a pile of loosely related text to an LLM and hoping produces confident
fabrication, which is the failure mode this product can least afford.

## Decision

Use one PostgreSQL database for everything: relational data, full-text search (`tsvector`), and
vector similarity (`pgvector`). The Docker Compose and Testcontainers setup both use the
`pgvector/pgvector` image from the start, so the extension is available when embeddings arrive
without migrating a populated database onto a different image.

Retrieval will be a single SQL query that filters on metadata first and ranks the survivors by a
combination of lexical and vector scores.

## Consequences

- Metadata filters and vector search happen in the same query, in one transaction, against one copy
  of the data. There is no window in which the index and the rows disagree.
- No second datastore to run, back up, secure or keep in sync — which also means one fewer place
  private data can leak from.
- pgvector's index types are less tunable than a dedicated vector database, and very large
  collections will eventually need attention (index type, dimensionality, partitioning). For a
  single person's lifetime of memories this is not the binding constraint.
- Embedding generation still sits behind an interface, so the provider can change without touching
  storage.

## Alternatives rejected

- **A dedicated vector database alongside Postgres.** Two systems to keep consistent, and
  cross-store metadata filtering has to be emulated in application code.
- **Elasticsearch or OpenSearch.** Strong lexical search, but another cluster to operate and another
  copy of highly private data, for capability Postgres already covers at this scale.
