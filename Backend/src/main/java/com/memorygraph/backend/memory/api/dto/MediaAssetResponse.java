package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.memory.domain.MediaAsset;

public record MediaAssetResponse(
        UUID id,
        String fileName,
        String mimeType,
        long sizeBytes,
        Integer widthPx,
        Integer heightPx,
        Double latitude,
        Double longitude,
        Instant capturedAt,
        Instant createdAt,
        String downloadPath) {

    public static MediaAssetResponse from(MediaAsset asset, UUID memoryId) {
        return new MediaAssetResponse(
                asset.getId(),
                asset.getFileName(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getWidthPx(),
                asset.getHeightPx(),
                asset.getLatitude(),
                asset.getLongitude(),
                asset.getCapturedAt(),
                asset.getCreatedAt(),
                "%s/memories/%s/media/%s".formatted(ApiPaths.V1, memoryId, asset.getId()));
    }
}
