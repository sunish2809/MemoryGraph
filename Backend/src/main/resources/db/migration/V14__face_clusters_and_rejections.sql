-- Face review: unnamed clusters, rejected suggestions, and grouping across photos.

ALTER TABLE face_detections
    ADD COLUMN cluster_id UUID;

CREATE INDEX ix_face_detections_cluster
    ON face_detections (user_id, cluster_id)
    WHERE cluster_id IS NOT NULL AND person_id IS NULL;

CREATE TABLE face_suggestion_rejections (
    face_id   UUID NOT NULL REFERENCES face_detections (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    PRIMARY KEY (face_id, person_id)
);

CREATE INDEX ix_face_rejections_person ON face_suggestion_rejections (person_id);
