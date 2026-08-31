package com.memorygraph.backend.memory.application.imports.googlephotos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

        byte[] bytes = readAll(file);
        GooglePhotosTakeoutReader.OpenedTakeout opened;
        try {
            opened = takeoutReader.open(bytes, fileName);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Could not read Takeout zip", ex);
        }

        ImportJob existing = importJobs.findByUserIdAndChecksum(userId, opened.checksum()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ImportJobStatus.FAILED) {
                existing.resetForRetry();
                events.publishEvent(new ImportJobQueued(existing.getId()));
            }
            return existing;
        }

        ImportJob job = ImportJob.pending(userId, ImportKind.GOOGLE_PHOTOS, fileName, opened.checksum(), zone);
        try {
            storage.store(job.key(), new ByteArrayInputStream(bytes), bytes.length);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not store Takeout zip", ex);
        }

        ImportJob saved = importJobs.save(job);
        events.publishEvent(new ImportJobQueued(saved.getId()));
        log.info("Queued Google Photos import {} for user {} ({} photos)", saved.getId(), userId,
                opened.photos().size());
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
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }

        GooglePhotosTakeoutReader.OpenedTakeout opened = takeoutReader.open(bytes, job.getFileName());
        User owner = requireUser(job.getUserId());
        int created = 0;

        for (GooglePhotosTakeoutReader.PhotoEntry photo : opened.photos()) {
            if (photo.bytes().length < SupportedMediaType.SIGNATURE_PROBE_BYTES) {
                continue;
            }
            byte[] header = new byte[SupportedMediaType.SIGNATURE_PROBE_BYTES];
            System.arraycopy(photo.bytes(), 0, header, 0, header.length);
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

            var displayable = heicToJpeg.toDisplayable(photo.bytes(), safeName, mediaType);
            StorageKey assetKey = StorageKey.forMemoryAsset(job.getUserId(), saved.getId(), displayable.fileName());
            StoredObject stored = storage.store(assetKey, new ByteArrayInputStream(displayable.bytes()),
                    displayable.bytes().length);
            MediaAsset asset = MediaAsset.of(assetKey, displayable.fileName(), displayable.mediaType().mimeType(),
                    stored.sizeBytes(), stored.checksum());
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

    public record Result(String label, int memoriesCreated) {
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Uploaded file could not be read", ex);
        }
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
