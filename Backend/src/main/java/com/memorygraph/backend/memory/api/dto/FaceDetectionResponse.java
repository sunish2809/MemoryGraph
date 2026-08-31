package com.memorygraph.backend.memory.api.dto;

import java.util.UUID;

/** One detected face on a photo or video still, with optional confirmed / suggested person. */
public record FaceDetectionResponse(
        UUID id,
        UUID memoryId,
        UUID assetId,
        double x,
        double y,
        double width,
        double height,
        UUID personId,
        String personName,
        UUID suggestedPersonId,
        String suggestedPersonName,
        Double confidence,
        UUID clusterId,
        Integer alsoSuggested,
        boolean ignored) {
}
