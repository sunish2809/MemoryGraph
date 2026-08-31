package com.memorygraph.backend.memory.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.SupportedMediaType;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.MediaAssetRepository;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves media bytes back to their owner.
 * <p>
 * Every read is authorised against the database first, which is why the API streams files itself
 * instead of exposing storage URLs. A private photo is never reachable by anyone holding a link, and
 * revoking access is immediate. The cost is that bytes pass through the application; when that becomes
 * the bottleneck, the answer is short-lived signed URLs issued after this same ownership check, not
 * public objects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAccessService {

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final HeicToJpegConverter heicToJpeg;

    public record DownloadableMedia(MediaAsset asset, Resource resource) {
    }

    @Transactional
    public DownloadableMedia openOwnedAsset(UUID userId, UUID memoryId, UUID assetId) {
        MediaAsset asset = assets.findOwnedAsset(assetId, memoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", assetId));
        convertStoredHeic(asset);
        return new DownloadableMedia(asset, storage.retrieve(asset.key()));
    }

    private void convertStoredHeic(MediaAsset asset) {
        if (!heicToJpeg.looksLikeHeic(asset.getMimeType())) {
            return;
        }
        try {
            byte[] original = storage.retrieve(asset.key()).getInputStream().readAllBytes();
            var displayable = heicToJpeg.toDisplayable(original, asset.getFileName(), SupportedMediaType.HEIC);
            if (displayable.mediaType() != SupportedMediaType.JPEG) {
                return;
            }
            StoredObject stored = storage.store(asset.key(), new ByteArrayInputStream(displayable.bytes()),
                    displayable.bytes().length);
            asset.replacePayload(displayable.fileName(), displayable.mediaType().mimeType(), stored.sizeBytes(),
                    stored.checksum());
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not convert HEIC on download for asset {}: {}", asset.getId(), ex.getMessage());
        }
    }
}
