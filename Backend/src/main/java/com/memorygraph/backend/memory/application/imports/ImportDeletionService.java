package com.memorygraph.backend.memory.application.imports;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
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
     * Stops an in-flight import (the worker notices the job row is gone and quits), deletes memories
     * created so far, then the job and the stored export.
     */
    @Transactional
    public int delete(UUID userId, UUID importId) {
        importJobs.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId));
        ImportJob locked = importJobs.findByIdForUpdate(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId));

        int deleted = deleteLinkedMemories(userId, importId, locked);

        try {
            storage.delete(locked.key());
        } catch (RuntimeException ex) {
            log.warn("Removed import job {} but could not delete export bytes: {}", importId, ex.getMessage());
        }

        importJobs.delete(locked);
        deleted += cleanupOrphanedImportMemories();
        log.info("Deleted import {} for user {} ({} memories)", importId, userId, deleted);
        return deleted;
    }

    /** Drops leftovers from a delete that raced an in-flight worker. */
    @Transactional
    public int cleanupOrphanedImportMemories() {
        int deleted = 0;
        var orphans = memories.findOrphanedImportMemories(Limit.of(50));
        for (MemoryRepository.MemoryOwnerId row : orphans) {
            try {
                memoryService.delete(row.getUserId(), row.getId());
                deleted++;
            } catch (RuntimeException ex) {
                log.warn("Could not delete orphaned import memory {}: {}", row.getId(), ex.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("Removed {} memories left behind by a deleted import", deleted);
        }
        return deleted;
    }

    private int deleteLinkedMemories(UUID userId, UUID importId, ImportJob job) {
        Set<UUID> memoryIds = new LinkedHashSet<>(memories.findIdsByUserIdAndImportJobId(userId, importId));
        if (StringUtils.hasText(job.getChatName())) {
            memoryIds.addAll(memories.findLegacyWhatsAppImportIds(userId, job.getChatName()));
        }
        int deleted = 0;
        for (UUID memoryId : memoryIds) {
            memoryService.delete(userId, memoryId);
            deleted++;
        }
        return deleted;
    }
}
