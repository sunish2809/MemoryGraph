-- Photo enrichment: EXIF location/capture time on assets; lock occurred_at when the user set it.

ALTER TABLE media_assets
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN captured_at TIMESTAMPTZ;

ALTER TABLE memories
    ADD COLUMN occurred_at_locked BOOLEAN NOT NULL DEFAULT FALSE;
