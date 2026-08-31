# 0010 — PostgreSQL full-text search, behind a `MemorySearcher` interface

Date: 2026-08-22

## Status

Accepted

## Context

The product is a search engine for one person's life. Phase 3 has to make memories findable by a
word, a type and a stretch of days, and it has to leave a place for semantic ranking to arrive in
Phase 4 without rewriting the API above it.

Three questions had to be answered together:

1. **Where does the index live?** The memories are already in PostgreSQL.
2. **Who maintains the searchable form of a row?** An application field, a trigger, or the database.
3. **What does a "searcher" return?** A score only this engine understands, or a ranked list of ids.

Getting any of these wrong would mean either a second copy of highly private text, or an API that
Phase 4 cannot implement.

## Decision

**One generated `tsvector` column, one SQL function, one Java interface.**

- `search_vector` is `GENERATED ALWAYS AS` a weighted concatenation of title (A), description (B)
  and content (C). A generated column cannot drift from the row it describes, and no future writer
  can forget to update it.
- The expression names the `'english'` text-search configuration explicitly. `default_text_search_config`
  is only `STABLE` because a session can change it; a generated column requires `IMMUTABLE`.
- User input is parsed by `memory_search_query(text)`: unquoted words become prefix terms so a
  partial word finds results while the user is still typing; quoted input is handed to
  `websearch_to_tsquery` for exact phrases. Unquoted input is tokenised with `to_tsvector` rather
  than split on whitespace, so it uses the same parser as the index and no fragment of what was
  typed is ever interpreted as `tsquery` syntax.
- Matching, ranking and highlighting happen in `FullTextMemorySearcher`, written as native SQL
  because JPQL has no vocabulary for `@@`, `ts_rank_cd` or `ts_headline`.
- The public contract is `MemorySearcher`: owner-scoped, filters are constraints not ranking hints,
  paging does not lose or repeat rows, and rank is expressed as position. No score is returned.
  Semantic search in Phase 4 is a second implementation of that interface; a hybrid of the two can
  fuse by position (reciprocal rank fusion) without needing a shared score scale.
- Matched words in a snippet are wrapped in inert `[[` `]]` markers, not `<mark>` tags. A snippet is
  assembled from the user's own text; shipping it as markup would invite the client to render HTML.

Date filters reuse `LocalDayRange` and `ViewerZone`, the same types the timeline uses, so "1 March"
means the same window on both endpoints.

## Consequences

- Stemming is English. Other scripts still index and match as whole words; they are not stemmed.
  Detecting a language per memory belongs with the importers that will bring in bulk foreign-language
  text.
- `ADD COLUMN … GENERATED` rewrites the table. Free at current size; on a large table the same
  change wants a nullable column, a batched backfill, then the constraint.
- Filters (owner, type, date) are applied before ranking, so a type filter cannot be "softened" by
  a high lexical score. That is the correct privacy and correctness default.
- The API does not change when embeddings arrive. The cost is that lexical search cannot yet answer
  "restaurant with Rahul" when those words never appear.

## Alternatives rejected

- **An application-maintained `tsvector` column.** A missed write on a new code path silently
  drops a memory from search. A generated column makes that impossible.
- **`LIKE` / `ILIKE`.** No stemming, no ranking, no index that survives a leading wildcard, and a
  prefix search while typing would be a sequential scan.
- **Elasticsearch or OpenSearch.** Strong lexical search, but another cluster to operate and another
  copy of highly private data, for capability Postgres already covers at this scale. Same rejection
  as [ADR 0003](0003-postgres-with-pgvector.md).
- **Exposing `ts_rank_cd` as a score.** Meaningless to a reader, and about to change scale once
  vector distance is in the mix. Position in the page is the ranking.
