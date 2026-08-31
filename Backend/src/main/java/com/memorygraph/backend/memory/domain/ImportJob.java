package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.memorygraph.backend.storage.StorageKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One uploaded export waiting to become memories. Separate from {@link ProcessingJob}: an import
 * creates many memories rather than enriching one.
 */
@Entity
@Table(name = "import_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, updatable = false)
    private ImportKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportJobStatus status;

    @Column(name = "storage_key", nullable = false, length = 512, updatable = false)
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "checksum", nullable = false, length = 64, updatable = false)
    private String checksum;

    @Column(name = "zone", nullable = false, length = 64, updatable = false)
    private String zone;

    @Column(name = "chat_name", length = 255)
    private String chatName;

    @Column(name = "memories_created", nullable = false)
    private int memoriesCreated;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ImportJob(UUID id, UUID userId, ImportKind kind, StorageKey storageKey, String fileName, String checksum,
            String zone) {
        this.id = id;
        this.userId = userId;
        this.kind = kind;
        this.status = ImportJobStatus.PENDING;
        this.storageKey = storageKey.value();
        this.fileName = fileName;
        this.checksum = checksum;
        this.zone = zone;
        this.memoriesCreated = 0;
    }

    public static ImportJob pending(UUID userId, ImportKind kind, String fileName, String checksum, String zone) {
        UUID id = UUID.randomUUID();
        return new ImportJob(id, userId, kind, StorageKey.forImport(userId, id, fileName), fileName, checksum, zone);
    }

    public StorageKey key() {
        return new StorageKey(storageKey);
    }

    public void markProcessing() {
        this.status = ImportJobStatus.PROCESSING;
        this.startedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markCompleted(String chatName, int memoriesCreated) {
        this.status = ImportJobStatus.COMPLETED;
        this.chatName = chatName;
        this.memoriesCreated = memoriesCreated;
        this.finishedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = ImportJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    /** Clears a failed run so the same checksum can be processed again. */
    public void resetForRetry() {
        this.status = ImportJobStatus.PENDING;
        this.errorMessage = null;
        this.startedAt = null;
        this.finishedAt = null;
        this.memoriesCreated = 0;
    }
}
