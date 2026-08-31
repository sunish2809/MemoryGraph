package com.memorygraph.backend.memory.application.imports;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;
import com.memorygraph.backend.memory.domain.ImportJobStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImportJobTransitions {

    private final ImportJobRepository jobs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ImportJob> claim(UUID jobId) {
        ImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        if (job.getStatus() != ImportJobStatus.PENDING && job.getStatus() != ImportJobStatus.FAILED) {
            return Optional.empty();
        }
        if (job.getStatus() == ImportJobStatus.FAILED) {
            job.resetForRetry();
        }
        job.markProcessing();
        return Optional.of(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, String chatName, int memoriesCreated) {
        ImportJob job = jobs.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", jobId));
        job.markCompleted(chatName, memoriesCreated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, String errorMessage) {
        jobs.findById(jobId).ifPresent(job -> job.markFailed(truncate(errorMessage)));
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Import failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
