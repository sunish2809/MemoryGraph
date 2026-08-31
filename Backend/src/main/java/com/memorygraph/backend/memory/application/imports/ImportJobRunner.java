package com.memorygraph.backend.memory.application.imports;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.memorygraph.backend.common.logging.RequestContext;
import com.memorygraph.backend.memory.application.imports.googlephotos.GooglePhotosImportService;
import com.memorygraph.backend.memory.application.imports.googlephotos.GooglePhotosPickerImportService;
import com.memorygraph.backend.memory.application.imports.whatsapp.WhatsAppImportService;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportKind;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportJobRunner {

    private final WhatsAppImportService whatsAppImportService;
    private final GooglePhotosImportService googlePhotosImportService;
    private final GooglePhotosPickerImportService googlePhotosPickerImportService;
    private final ImportJobTransitions transitions;

    public void run(ImportJob job) {
        MDC.put(RequestContext.USER_ID_KEY, job.getUserId().toString());
        long started = System.nanoTime();
        try {
            String label;
            int created;
            if (job.getKind() == ImportKind.WHATSAPP) {
                WhatsAppImportService.Result result = whatsAppImportService.process(job);
                label = result.chatName();
                created = result.memoriesCreated();
            } else if (job.getKind() == ImportKind.GOOGLE_PHOTOS) {
                GooglePhotosImportService.Result result = googlePhotosImportService.process(job);
                label = result.label();
                created = result.memoriesCreated();
            } else if (job.getKind() == ImportKind.GOOGLE_PHOTOS_PICKER) {
                GooglePhotosPickerImportService.Result result = googlePhotosPickerImportService.process(job);
                label = result.label();
                created = result.memoriesCreated();
            } else {
                throw new IllegalStateException("Unsupported import kind: " + job.getKind());
            }
            transitions.complete(job.getId(), label, created);
            log.info("Import job {} completed with {} memories in {}ms", job.getId(), created,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (Exception ex) {
            log.error("Import job {} failed after {}ms", job.getId(), (System.nanoTime() - started) / 1_000_000, ex);
            try {
                transitions.fail(job.getId(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } catch (Exception bookkeeping) {
                log.error("Could not record failure of import job {}", job.getId(), bookkeeping);
            }
        } finally {
            MDC.remove(RequestContext.USER_ID_KEY);
        }
    }
}
