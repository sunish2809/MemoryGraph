-- Phase 4: semantic retrieval.
--
-- One embedding per memory, of the same normalised text that full-text search already uses. Chunking
-- can arrive later for long transcripts; a personal archive of notes and photo captions does not need
-- it yet, and a single vector keeps hybrid ranking simple.
--
-- The column is owned by native SQL rather than by the JPA entity: Hibernate has no first-class
-- mapping for pgvector that we want to depend on, and the only writers are the embedding processor
-- and the semantic searcher, both of which speak SQL already.

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE memories
    ADD COLUMN embedding vector(1536);

-- Cosine distance. Built without concurrent create because this is still a single-user archive at a
-- size where a brief lock on memories is cheaper than the operational complexity of CONCURRENTLY.
CREATE INDEX ix_memories_embedding_hnsw
    ON memories
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
