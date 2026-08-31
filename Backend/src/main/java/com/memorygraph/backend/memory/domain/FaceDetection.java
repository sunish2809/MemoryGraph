package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One face found on a photo. Bounding box is normalised 0–1 relative to the image. The embedding is
 * written via JDBC (same pattern as memory text embeddings) so JPA never maps the vector column.
 */
@Entity
@Table(name = "face_detections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceDetection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    @Column(name = "width", nullable = false)
    private double width;

    @Column(name = "height", nullable = false)
    private double height;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "suggested_person_id")
    private UUID suggestedPersonId;

    @Column(name = "cluster_id")
    private UUID clusterId;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "ignored", nullable = false)
    private boolean ignored;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private FaceDetection(
            UUID memoryId, UUID userId, UUID assetId, double x, double y, double width, double height) {
        this.id = UUID.randomUUID();
        this.memoryId = memoryId;
        this.userId = userId;
        this.assetId = assetId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public static FaceDetection create(
            UUID memoryId, UUID userId, UUID assetId, double x, double y, double width, double height) {
        return new FaceDetection(memoryId, userId, assetId, x, y, width, height);
    }

    public void suggest(UUID personId, Double confidence) {
        this.suggestedPersonId = personId;
        this.confidence = confidence;
    }

    public void assignPerson(UUID personId) {
        this.personId = personId;
        this.suggestedPersonId = personId;
        this.ignored = false;
    }

    public void ignore() {
        this.ignored = true;
        this.suggestedPersonId = null;
        this.confidence = null;
    }

    public void restore() {
        this.ignored = false;
    }

    public void assignCluster(UUID clusterId) {
        this.clusterId = clusterId;
    }

    public void clearPerson() {
        this.personId = null;
    }

    public void clearSuggestion() {
        this.suggestedPersonId = null;
        this.confidence = null;
    }
}
