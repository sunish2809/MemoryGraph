package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.memorygraph.backend.user.domain.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The central abstraction of the product: one thing that happened, from any source, placed on the
 * user's timeline and made searchable.
 * <p>
 * Every memory belongs to exactly one user and is never readable across users. Queries must always
 * be scoped by owner, which is why the repository exposes only owner-scoped finders.
 */
@Entity
@Table(name = "memories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private MemoryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MemorySource source;

    /** Set when this memory was created by a bulk import; used to delete and re-upload cleanly. */
    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Normalised, searchable text for this memory: the note body, an image caption plus OCR text, a
     * transcript, and so on. This is what full-text and semantic search run against.
     */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** When the memory happened, as opposed to when it was imported. Drives the timeline. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private ProcessingStatus processingStatus;

    /**
     * When true, enrichment must not overwrite {@link #occurredAt} from EXIF — the user chose the
     * time explicitly at upload.
     */
    @Column(name = "occurred_at_locked", nullable = false)
    private boolean occurredAtLocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Deleting a memory must also delete its files, so the association cascades. Removing the rows is
     * only half the job: the owning service is responsible for deleting the bytes from object storage.
     * <p>
     * Lazy, and loaded explicitly by the queries that need it. Batching bounds the damage if some
     * future code path iterates memories without asking for their media up front.
     */
    @OneToMany(mappedBy = "memory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 64)
    private List<MediaAsset> assets = new ArrayList<>();

    private Memory(User user, MemoryType type, MemorySource source, Instant occurredAt) {
        this.user = user;
        this.type = type;
        this.source = source;
        this.occurredAt = occurredAt;
        this.processingStatus = ProcessingStatus.PENDING;
        this.occurredAtLocked = false;
    }

    public static Memory create(User owner, MemoryType type, MemorySource source, Instant occurredAt) {
        return new Memory(owner, type, source, occurredAt);
    }

    public static Memory create(User owner, MemoryType type, MemorySource source, Instant occurredAt,
            boolean occurredAtLocked) {
        Memory memory = new Memory(owner, type, source, occurredAt);
        memory.occurredAtLocked = occurredAtLocked;
        return memory;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public List<MediaAsset> getAssets() {
        return Collections.unmodifiableList(assets);
    }

    public void attach(MediaAsset asset) {
        assets.add(asset);
        asset.assignTo(this);
    }

    public void describe(String title, String description, String content) {
        this.title = title;
        this.description = description;
        this.content = content;
    }

    public void linkImport(UUID importJobId) {
        this.importJobId = importJobId;
    }

    /**
     * Replaces the searchable text. Called by the processing pipeline as enrichment produces better
     * text than the user originally supplied (a caption, OCR output, a transcript).
     */
    public void updateSearchableContent(String content) {
        this.content = content;
    }

    public void moveTo(Instant occurredAt) {
        if (occurredAtLocked) {
            return;
        }
        this.occurredAt = occurredAt;
    }

    public void lockOccurredAt() {
        this.occurredAtLocked = true;
    }

    /**
     * User correction of title, caption, searchable text and/or when it happened. An explicit date
     * always wins, including over a previously locked EXIF time.
     */
    public void applyEdit(String title, String description, String content, Instant occurredAt) {
        if (title != null) {
            this.title = title.isBlank() ? null : title.strip();
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description.strip();
        }
        if (content != null) {
            this.content = content.isBlank() ? null : content;
        }
        if (occurredAt != null) {
            this.occurredAt = occurredAt;
            this.occurredAtLocked = true;
        }
    }

    public void markProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
    }

    public void markProcessed(ProcessingStatus terminalStatus) {
        this.processingStatus = terminalStatus;
    }
}
