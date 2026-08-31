package com.memorygraph.backend.memory.application.imports.googlephotos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.integration.google.GoogleOAuthService;
import com.memorygraph.backend.integration.google.GooglePhotosPickerClient;
import com.memorygraph.backend.integration.google.GooglePhotosPickerClient.PickedMediaItem;
import com.memorygraph.backend.integration.google.GooglePhotosPickerClient.PickerSession;
import com.memorygraph.backend.integration.google.api.GooglePickerSessionResponse;
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
import com.memorygraph.backend.memory.domain.MemoryType;
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
public class GooglePhotosPickerImportService {

    private final GoogleOAuthService googleOAuth;
    private final GooglePhotosPickerClient picker;
    private final ImportJobRepository importJobs;
    private final MemoryRepository memories;
    private final ProcessingJobRepository processingJobs;
    private final UserRepository users;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final HeicToJpegConverter heicToJpeg;

    public GooglePickerSessionResponse createSession(UUID userId) {
        String accessToken = googleOAuth.requireAccessToken(userId);
        PickerSession session = picker.createSession(accessToken);
        String pickerUri = session.pickerUri();
        if (!pickerUri.contains("/autoclose")) {
            pickerUri = pickerUri.endsWith("/") ? pickerUri + "autoclose" : pickerUri + "/autoclose";
        }
        return new GooglePickerSessionResponse(
                session.id(),
                pickerUri,
                session.pollIntervalMs(),
                session.selectionComplete());
    }

    public GooglePickerSessionResponse getSession(UUID userId, String sessionId) {
        String accessToken = googleOAuth.requireAccessToken(userId);
        PickerSession session = picker.getSession(accessToken, sessionId);
        return new GooglePickerSessionResponse(
                session.id(),
                session.pickerUri(),
                session.pollIntervalMs(),
                session.selectionComplete());
    }

    @Transactional
    public ImportJob startImport(UUID userId, String sessionId, String zone) {
        requireUser(userId);
        ViewerZone.parse(zone);
        String accessToken = googleOAuth.requireAccessToken(userId);
        PickerSession session = picker.getSession(accessToken, sessionId);
        if (!session.selectionComplete()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Finish selecting photos in Google Photos before importing");
        }

        List<PickedMediaItem> items = picker.listAllMediaItems(accessToken, sessionId);
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No photos were selected in Google Photos");
        }

        String checksum = GoogleOAuthService.sha256Hex(items.stream()
                .map(PickedMediaItem::id)
                .sorted()
                .collect(Collectors.joining(",")));

        ImportJob existing = importJobs.findByUserIdAndChecksum(userId, checksum).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ImportJobStatus.FAILED) {
                existing.resetForRetry();
                events.publishEvent(new ImportJobQueued(existing.getId()));
            }
            return existing;
        }

        byte[] manifest = json.writeValueAsBytes(new PickerManifest(sessionId, items));

        String fileName = "google-photos-picker.json";
        ImportJob job = ImportJob.pending(userId, ImportKind.GOOGLE_PHOTOS_PICKER, fileName, checksum, zone);
        try {
            storage.store(job.key(), new ByteArrayInputStream(manifest), manifest.length);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not store picker selection", ex);
        }

        ImportJob saved = importJobs.save(job);
        events.publishEvent(new ImportJobQueued(saved.getId()));
        log.info("Queued Google Photos Picker import {} for user {} ({} items)", saved.getId(), userId, items.size());
        return saved;
    }

    @Transactional
    public Result process(ImportJob job) throws IOException {
        Resource resource = storage.retrieve(job.key());
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }
        PickerManifest manifest = json.readValue(bytes, PickerManifest.class);
        String accessToken = googleOAuth.requireAccessToken(job.getUserId());
        User owner = requireUser(job.getUserId());
        int created = 0;

        List<PickedMediaItem> items = manifest.items().stream()
                .sorted(Comparator.comparing(PickedMediaItem::createTime, Comparator.nullsLast(String::compareTo)))
                .toList();

        for (PickedMediaItem item : items) {
            if (item.baseUrl() == null) {
                continue;
            }
            byte[] media;
            try {
                media = picker.downloadBytes(accessToken, item.baseUrl(), item.isVideo());
            } catch (ApiException ex) {
                log.warn("Skipping picker item {}: {}", item.id(), ex.getMessage());
                continue;
            }
            if (media.length < SupportedMediaType.SIGNATURE_PROBE_BYTES) {
                continue;
            }
            byte[] header = new byte[SupportedMediaType.SIGNATURE_PROBE_BYTES];
            System.arraycopy(media, 0, header, 0, header.length);
            SupportedMediaType mediaType = SupportedMediaType.detect(header)
                    .or(() -> SupportedMediaType.fromMimeType(item.mimeType()))
                    .orElse(null);
            if (mediaType == null) {
                log.warn("Skipping picker item {}: unsupported type (mime={}, name={})", item.id(),
                        item.mimeType(), item.filename());
                continue;
            }
            if (mediaType.memoryType() == MemoryType.AUDIO) {
                continue;
            }

            Instant occurredAt = parseInstant(item.createTime());
            Memory memory = Memory.create(owner, mediaType.memoryType(), MemorySource.IMPORT, occurredAt,
                    item.createTime() != null);
            String rawName = item.filename() != null ? item.filename() : "google-photo";
            String safeName = uploadValidator.sanitiseFileName(rawName);
            memory.describe(safeName, "Google Photos · Picker", null);
            memory.linkImport(job.getId());
            Memory saved = memories.save(memory);

            var displayable = heicToJpeg.toDisplayable(media, safeName, mediaType);
            StorageKey assetKey = StorageKey.forMemoryAsset(job.getUserId(), saved.getId(), displayable.fileName());
            StoredObject stored = storage.store(assetKey, new ByteArrayInputStream(displayable.bytes()),
                    displayable.bytes().length);
            MediaAsset asset = MediaAsset.of(assetKey, displayable.fileName(), displayable.mediaType().mimeType(),
                    stored.sizeBytes(), stored.checksum());
            if (item.createTime() != null) {
                asset.recordCapture(occurredAt, null, null);
            }
            saved.attach(asset);

            ProcessingJob meta = processingJobs.save(
                    ProcessingJob.pending(saved.getId(), job.getUserId(), ProcessingJobType.MEDIA_METADATA));
            events.publishEvent(new ProcessingJobQueued(meta.getId()));
            created++;
        }

        if (created == 0) {
            throw new IllegalArgumentException("No supported media could be imported from the Google Photos selection");
        }
        return new Result("Google Photos Picker", created);
    }

    public record Result(String label, int memoriesCreated) {
    }

    public record PickerManifest(String sessionId, List<PickedMediaItem> items) {
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
