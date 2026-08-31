-- Background / crowd faces the owner does not want to name.

ALTER TABLE face_detections
    ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX ix_face_detections_user_needs_label
    ON face_detections (user_id)
    WHERE person_id IS NULL AND ignored = false;
