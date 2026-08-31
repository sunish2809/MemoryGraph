package com.memorygraph.backend.memory.application.imports;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.application.MemoryService;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes an import job (freeing its checksum for re-upload) and the memories it created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportDeletionService {

    private final ImportJobRepository importJobs;
    private final MemoryRepository memories;
    private final MemoryService memoryService;
    private final StorageService storage;

    @Transactional(readOnly = true)
    public List<ImportJob> list(UUID userId) {
        return importJobs.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Deletes memories from this import (linked by {@code import_job_id}, plus legacy WhatsApp
     * matches by chat name), then the job row and stored export bytes.
     */
    @Transactional
    public int delete(UUID userId, UUID importId) {
        ImportJob job = importJobs.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId));

        Set<UUID> memoryIds = new LinkedHashSet<>(memories.findIdsByUserIdAndImportJobId(userId, importId));
        if (StringUtils.hasText(job.getChatName())) {
            memoryIds.addAll(memories.findLegacyWhatsAppImportIds(userId, job.getChatName()));
        }

        int deleted = 0;
        for (UUID memoryId : memoryIds) {
            memoryService.delete(userId, memoryId);
            deleted++;
        }

        try {
            storage.delete(job.key());
        } catch (RuntimeException ex) {
            log.warn("Removed import job {} but could not delete export bytes: {}", importId, ex.getMessage());
        }

        importJobs.delete(job);
        log.info("Deleted import {} for user {} ({} memories)", importId, userId, deleted);
        return deleted;
    }
}
