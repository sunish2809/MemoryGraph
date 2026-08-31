package com.memorygraph.backend.memory.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.integration.google.api.GooglePickerImportRequest;
import com.memorygraph.backend.integration.google.api.GooglePickerSessionResponse;
import com.memorygraph.backend.memory.api.dto.ImportJobResponse;
import com.memorygraph.backend.memory.application.imports.ImportDeletionService;
import com.memorygraph.backend.memory.application.imports.googlephotos.GooglePhotosImportService;
import com.memorygraph.backend.memory.application.imports.googlephotos.GooglePhotosPickerImportService;
import com.memorygraph.backend.memory.application.imports.whatsapp.WhatsAppImportService;
import com.memorygraph.backend.memory.domain.ImportJobRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/imports")
@Validated
@RequiredArgsConstructor
public class ImportController {

    private final WhatsAppImportService whatsAppImportService;
    private final GooglePhotosImportService googlePhotosImportService;
    private final GooglePhotosPickerImportService googlePhotosPickerImportService;
    private final ImportDeletionService importDeletionService;
    private final ImportJobRepository importJobs;

    @GetMapping
    public ApiResponse<List<ImportJobResponse>> listImports() {
        return ApiResponse.success(importDeletionService.list(CurrentUser.requireId()).stream()
                .map(ImportJobResponse::from)
                .toList());
    }

    @PostMapping(path = "/whatsapp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ImportJobResponse> importWhatsApp(
            @RequestPart("file") MultipartFile file,
            @RequestParam String zone) {
        requireZone(zone);
        return ApiResponse.success(ImportJobResponse.from(
                whatsAppImportService.startImport(CurrentUser.requireId(), file, zone.strip())));
    }

    /**
     * Google Takeout zip of Photos. Full-library OAuth sync is not available to third-party apps
     * after Google's 2025 Library API changes; Takeout is the bulk path.
     */
    @PostMapping(path = "/google-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ImportJobResponse> importGooglePhotos(
            @RequestPart("file") MultipartFile file,
            @RequestParam String zone) {
        requireZone(zone);
        return ApiResponse.success(ImportJobResponse.from(
                googlePhotosImportService.startImport(CurrentUser.requireId(), file, zone.strip())));
    }

    /** Opens Google's Photos Picker UI; poll until {@code mediaItemsSet}, then call import. */
    @PostMapping("/google-photos/picker/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GooglePickerSessionResponse> createPickerSession() {
        return ApiResponse.success(googlePhotosPickerImportService.createSession(CurrentUser.requireId()));
    }

    @GetMapping("/google-photos/picker/sessions/{sessionId}")
    public ApiResponse<GooglePickerSessionResponse> getPickerSession(@PathVariable String sessionId) {
        return ApiResponse.success(
                googlePhotosPickerImportService.getSession(CurrentUser.requireId(), sessionId));
    }

    @PostMapping("/google-photos/picker/sessions/{sessionId}/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ImportJobResponse> importPickerSession(
            @PathVariable String sessionId,
            @Valid @RequestBody GooglePickerImportRequest body) {
        requireZone(body.zone());
        return ApiResponse.success(ImportJobResponse.from(googlePhotosPickerImportService
                .startImport(CurrentUser.requireId(), sessionId, body.zone().strip())));
    }

    @GetMapping("/{importId}")
    public ApiResponse<ImportJobResponse> getImport(@PathVariable UUID importId) {
        return ApiResponse.success(ImportJobResponse.from(importJobs
                .findByIdAndUserId(importId, CurrentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId))));
    }

    /**
     * Deletes the import job (so the same export can be re-uploaded) and memories created from it.
     */
    @DeleteMapping("/{importId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImport(@PathVariable UUID importId) {
        importDeletionService.delete(CurrentUser.requireId(), importId);
    }

    private static void requireZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "zone is required");
        }
    }
}
