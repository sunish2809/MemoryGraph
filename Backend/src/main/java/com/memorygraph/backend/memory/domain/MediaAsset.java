package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.memorygraph.backend.storage.StorageKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The binary file behind a memory, plus what we know about it. The bytes live in object storage;
 * this row only points at them.
 * <p>
 * A memory can own several assets — an upload today, a generated thumbnail or transcoded version
 * later — which is why this is a collection rather than a single column on {@link Memory}.
 */
@Entity
@Table(name = "media_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memory_id", nullable = false, updatable = false)
    private Memory memory;

    @Column(name = "storage_key", nullable = false, length = 512, updatable = false)
    private String storageKey;

    /** The name the file had on the user's device. Shown in the UI, never used as a path. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 of the stored bytes, hex encoded. Detects corruption and enables later de-duplication. */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private MediaAsset(StorageKey storageKey, String fileName, String mimeType, long sizeBytes, String checksum) {
        this.storageKey = storageKey.value();
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
    }

    public static MediaAsset of(StorageKey storageKey, String fileName, String mimeType, long sizeBytes,
            String checksum) {
        return new MediaAsset(storageKey, fileName, mimeType, sizeBytes, checksum);
    }

    public StorageKey key() {
        return new StorageKey(storageKey);
    }

    /** Set by {@link Memory#attach(MediaAsset)}; keeps both sides of the association consistent. */
    void assignTo(Memory memory) {
        this.memory = memory;
    }

    public void recordImageDimensions(int widthPx, int heightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public void recordCapture(Instant capturedAt, Double latitude, Double longitude) {
        if (capturedAt != null) {
            this.capturedAt = capturedAt;
        }
        if (latitude != null && longitude != null) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** After converting HEIC to JPEG in place (same storage key, new bytes). */
    public void replacePayload(String fileName, String mimeType, long sizeBytes, String checksum) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
    }
}
