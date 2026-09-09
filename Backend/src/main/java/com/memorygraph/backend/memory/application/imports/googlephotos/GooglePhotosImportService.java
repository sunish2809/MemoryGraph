package com.memorygraph.backend.memory.application.imports.googlephotos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.memory.application.PlaceLinkService;
import com.memorygraph.backend.memory.application.imports.ImportAbortedException;
import com.memorygraph.backend.memory.application.imports.ImportJobQueued;
import com.memorygraph.backend.memory.application.processing.ProcessingJobQueued;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.SupportedMediaType;
import com.memorygraph.backend.memory.application.upload.UploadValidator;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;
import com.memorygraph.backend.memory.domain.ImportJobStatus;
import com.memorygraph.backend.memory.domain.ImportKind;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.storage.StorageKey;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePhotosImportService {

    private final ImportJobRepository importJobs;
    private final MemoryRepository memories;
    private final ProcessingJobRepository processingJobs;
    private final UserRepository users;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final GooglePhotosTakeoutReader takeoutReader;
    private final PlaceLinkService placeLinks;
    private final ApplicationEventPublisher events;
    private final HeicToJpegConverter heicToJpeg;

    @Transactional
    public ImportJob startImport(UUID userId, MultipartFile file, String zone) {
        requireUser(userId);
        ViewerZone.parse(zone);
        String fileName = uploadValidator.validateImportPayload(file);
        if (!fileName.toLowerCase().endsWith(".zip")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Google Photos Takeout must be a .zip file");
        }

        UUID importId = UUID.randomUUID();
        StorageKey key = StorageKey.forImport(userId, importId, fileName);
        StoredObject stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(key, in, file.getSize());
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not store Takeout zip", ex);
        }

        Path storedPath;
        try {
            storedPath = storage.retrieve(key).getFile().toPath();
            takeoutReader.requirePhotos(storedPath);
        } catch (IllegalArgumentException ex) {
            storage.delete(key);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        } catch (IOException ex) {
            storage.delete(key);
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Could not read Takeout zip", ex);
        }

        ImportJob existing = importJobs.findByUserIdAndChecksum(userId, stored.checksum()).orElse(null);
        if (existing != null) {
            storage.delete(key);
            if (existing.getStatus() == ImportJobStatus.FAILED) {
                existing.resetForRetry();
                events.publishEvent(new ImportJobQueued(existing.getId()));
            }
            return existing;
        }

        ImportJob job = ImportJob.pending(importId, userId, ImportKind.GOOGLE_PHOTOS, key, fileName,
                stored.checksum(), zone);
        ImportJob saved = importJobs.save(job);
        events.publishEvent(new ImportJobQueued(saved.getId()));
        log.info("Queued Google Photos import {} for user {} ({} bytes)", saved.getId(), userId, stored.sizeBytes());
        return saved;
    }

    @Transactional(readOnly = true)
    public ImportJob get(UUID userId, UUID importId) {
        return importJobs.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId));
    }

    @Transactional
    public Result process(ImportJob job) throws IOException {
        Resource resource = storage.retrieve(job.key());
        Path stored = resource.getFile().toPath();

        try (GooglePhotosTakeoutReader.OpenedTakeout opened = takeoutReader.openStored(stored)) {
            User owner = requireUser(job.getUserId());
            int created = 0;

            for (GooglePhotosTakeoutReader.PhotoEntry photo : opened.photos()) {
                if (importJobs.findById(job.getId()).isEmpty()) {
                    throw new ImportAbortedException(job.getId());
                }
                byte[] photoBytes = opened.readPhoto(photo);
                if (photoBytes.length < SupportedMediaType.SIGNATURE_PROBE_BYTES) {
                    continue;
                }
                byte[] header = new byte[SupportedMediaType.SIGNATURE_PROBE_BYTES];
                System.arraycopy(photoBytes, 0, header, 0, header.length);
                SupportedMediaType mediaType;
                try {
                    mediaType = uploadValidator.requireImage(header);
                } catch (ApiException ex) {
                    continue;
                }

                Instant occurredAt = photo.takenAt() != null ? photo.takenAt() : Instant.now();
                Memory memory = Memory.create(owner, mediaType.memoryType(), MemorySource.IMPORT, occurredAt,
                        photo.takenAt() != null);
                String safeName = uploadValidator.sanitiseFileName(photo.fileName());
                String albumHint = albumFromPath(photo.relativePath());
                memory.describe(safeName, albumHint != null ? "Google Photos · " + albumHint : "Google Photos", null);
                memory.linkImport(job.getId());
                Memory saved = memories.save(memory);

                var displayable = heicToJpeg.toDisplayable(photoBytes, safeName, mediaType);
                StorageKey assetKey = StorageKey.forMemoryAsset(job.getUserId(), saved.getId(), displayable.fileName());
                StoredObject storedAsset = storage.store(assetKey, new ByteArrayInputStream(displayable.bytes()),
                        displayable.bytes().length);
                MediaAsset asset = MediaAsset.of(assetKey, displayable.fileName(), displayable.mediaType().mimeType(),
                        storedAsset.sizeBytes(), storedAsset.checksum());
                if (photo.latitude() != null && photo.longitude() != null) {
                    asset.recordCapture(photo.takenAt(), photo.latitude(), photo.longitude());
                }
                saved.attach(asset);

                if (photo.latitude() != null && photo.longitude() != null) {
                    placeLinks.upsertAndLink(job.getUserId(), saved.getId(), photo.latitude(), photo.longitude());
                }

                ProcessingJob meta = processingJobs.save(
                        ProcessingJob.pending(saved.getId(), job.getUserId(), ProcessingJobType.MEDIA_METADATA));
                events.publishEvent(new ProcessingJobQueued(meta.getId()));
                created++;
            }

            if (created == 0) {
                throw new IllegalArgumentException("No supported image files could be imported from the Takeout zip");
            }
            return new Result("Google Photos", created);
        }
    }

    public record Result(String label, int memoriesCreated) {
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** Prefer the folder under "Google Photos/" as a human album hint. */
    static String albumFromPath(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        String[] parts = relativePath.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equalsIgnoreCase("Google Photos") && i + 1 < parts.length - 1) {
                return parts[i + 1];
            }
        }
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return null;
    }
}
