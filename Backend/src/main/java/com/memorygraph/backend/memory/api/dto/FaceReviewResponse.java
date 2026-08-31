package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.MemoryType;

/** Unlabeled faces grouped so the same unknown person can be named in one pass. */
public record FaceReviewResponse(
        long unlabeledCount,
        long suggestedCount,
        List<FaceClusterGroup> groups) {

    public record FaceClusterGroup(
            UUID clusterId,
            int size,
            UUID suggestedPersonId,
            String suggestedPersonName,
            List<FaceReviewItem> faces) {
    }

    public record FaceReviewItem(
            UUID id,
            UUID memoryId,
            String memoryTitle,
            MemoryType memoryType,
            Instant occurredAt,
            UUID assetId,
            String downloadPath,
            double x,
            double y,
            double width,
            double height,
            UUID suggestedPersonId,
            String suggestedPersonName,
            Double confidence,
            UUID clusterId) {
    }
}
