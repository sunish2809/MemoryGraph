# 0011 — Embeddings, hybrid retrieval, and grounded Ask

Date: 2026-08-22

## Status

Accepted

## Context

Phase 4 has to answer questions about a person's life from their own memories. That requires three
things that do not yet exist: a vector for each memory, a way to rank by meaning as well as by words,
and a generation step that is not allowed to invent a past the user never recorded.

Phase 3 already left a seam (`MemorySearcher`) and a processing pipeline that can host another job
type. The database image has been `pgvector/pgvector` since Phase 1. The remaining decisions are how
vectors are stored, how the two ranks are combined, and what happens when no OpenAI key is configured.

## Decision

**One embedding per memory, in PostgreSQL, behind our own interfaces.**

- `CREATE EXTENSION vector` and a `memories.embedding vector(1536)` column (matching
  `text-embedding-3-small`). Written by JDBC, not mapped on the JPA entity — the only writers are the
  embedding processor and the semantic searcher.
- An `EMBEDDING` processing job. Text memories enqueue it on create; uploads enqueue it after
  `MEDIA_METADATA` completes. Embedding failure does **not** flip `processing_status`: a memory is
  useful without a vector.
- `EmbeddingClient` is the provider seam. Production uses Spring AI's OpenAI model when
  `spring.ai.model.embedding=openai`; otherwise a deterministic hasher so tests and keyless deploys
  still exercise the pipeline. The hasher is not semantic similarity and must not be treated as such.
- `HybridMemorySearcher` is the primary `MemorySearcher`. It pulls candidate pages from full-text and
  semantic search and fuses them with reciprocal rank fusion (k=60). Rank remains position, never a
  raw score — the same contract Phase 3 established for Phase 4.
- Ask is retrieve-then-generate: lexical + semantic fused like Search, then an `AnswerGenerator`. Ask
  lexical parsing uses `memory_ask_query` (OR of terms) so natural-language questions are not
  defeated by verbs that never appear in the note; Search keeps AND via `memory_search_query`. Ask
  may also keep semantic-only hits that clear the distance ceiling (paraphrases). With a chat model
  configured, the LLM is instructed to use only the retrieved memories and to say so when they are
  insufficient. Without a model, a retrieval-only generator lists the sources honestly. Sources are
  always returned.

## Consequences

- Metadata filters (owner, type, date) still apply before ranking, on both lexical and semantic paths.
- A deployment without `OPENAI_API_KEY` boots and serves Ask; answers are retrieval summaries, not
  synthesised prose.
- Changing embedding model or dimension requires a migration and a re-embed of existing rows.
- Chunking long transcripts is deferred; one vector per memory is enough for notes and photo captions.

## Alternatives rejected

- **Spring AI `VectorStore` as the system of record.** We already own `Memory` and owner-scoped
  search. A second table of opaque documents would duplicate private text and fight our filters.
- **Exposing cosine distance as a score.** Meaningless next to `ts_rank_cd`; RRF by position is how
  the two combine without a shared scale.
- **Requiring an API key to start.** Would make CI and local Compose depend on a secret for features
  that can be verified without one.
