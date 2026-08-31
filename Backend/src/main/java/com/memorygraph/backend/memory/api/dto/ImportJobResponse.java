package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobStatus;
import com.memorygraph.backend.memory.domain.ImportKind;

public record ImportJobResponse(
        UUID id,
        ImportKind kind,
        ImportJobStatus status,
        String fileName,
        String zone,
        String chatName,
        int memoriesCreated,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt) {

    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getKind(),
                job.getStatus(),
                job.getFileName(),
                job.getZone(),
                job.getChatName(),
                job.getMemoriesCreated(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
