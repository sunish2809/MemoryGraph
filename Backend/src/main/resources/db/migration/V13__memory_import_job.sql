-- Link memories to the import that created them so deletes can free re-uploads.

ALTER TABLE memories
    ADD COLUMN import_job_id UUID REFERENCES import_jobs (id) ON DELETE SET NULL;

CREATE INDEX ix_memories_import_job ON memories (import_job_id)
    WHERE import_job_id IS NOT NULL;
