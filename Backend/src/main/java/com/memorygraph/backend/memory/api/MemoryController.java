package com.memorygraph.backend.memory.api;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.api.PageResponse;
import com.memorygraph.backend.memory.api.dto.ConfirmFaceRequest;
import com.memorygraph.backend.memory.api.dto.CreateTextMemoryRequest;
import com.memorygraph.backend.memory.api.dto.FaceDetectionResponse;
import com.memorygraph.backend.memory.api.dto.LinkedPersonResponse;
import com.memorygraph.backend.memory.api.dto.MemoryResponse;
import com.memorygraph.backend.memory.api.dto.MemoryStatsResponse;
import com.memorygraph.backend.memory.api.dto.MemorySummaryResponse;
import com.memorygraph.backend.memory.api.dto.TagPersonRequest;
import com.memorygraph.backend.memory.api.dto.UpdateMemoryRequest;
import com.memorygraph.backend.memory.application.MediaAccessService;
import com.memorygraph.backend.memory.application.MemoryService;
import com.memorygraph.backend.memory.application.face.FaceService;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Person;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/memories")
@Validated
@RequiredArgsConstructor
public class MemoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MemoryService memoryService;
    private final MediaAccessService mediaAccessService;
    private final FaceService faceService;

    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemoryResponse> createTextMemory(@Valid @RequestBody CreateTextMemoryRequest request) {
        return ApiResponse.success(MemoryResponse.from(memoryService.createTextMemory(
                CurrentUser.requireId(), request.title(), request.description(), request.content(),
                request.occurredAt())));
    }

    /**
     * Multipart rather than JSON with a base64 body: it streams, so a large file never has to be held
     * in memory, and it costs a third less on the wire.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemoryResponse> uploadMemory(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Instant occurredAt) {

        return ApiResponse.success(MemoryResponse.from(memoryService.createUploadedMemory(
                CurrentUser.requireId(), file, title, description, occurredAt)));
    }

    @GetMapping
    public ApiResponse<PageResponse<MemorySummaryResponse>> listMemories(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(
                PageResponse.of(memoryService.list(CurrentUser.requireId(), pageable), MemorySummaryResponse::from));
    }

    /** Mapped before {@code /{memoryId}} would ever be considered, since "stats" is not a UUID. */
    @GetMapping("/stats")
    public ApiResponse<MemoryStatsResponse> stats() {
        return ApiResponse.success(memoryService.stats(CurrentUser.requireId()));
    }

    @GetMapping("/{memoryId}")
    public ApiResponse<MemoryResponse> getMemory(@PathVariable UUID memoryId) {
        UUID userId = CurrentUser.requireId();
        MemoryService.MemoryDetail detail = memoryService.getDetail(userId, memoryId);
        return ApiResponse.success(MemoryResponse.from(
                detail.memory(),
                detail.messages(),
                detail.people(),
                faceService.listForMemory(userId, memoryId)));
    }

    @PatchMapping("/{memoryId}")
    public ApiResponse<MemoryResponse> updateMemory(
            @PathVariable UUID memoryId, @Valid @RequestBody UpdateMemoryRequest request) {
        UUID userId = CurrentUser.requireId();
        memoryService.update(userId, memoryId, request.title(), request.description(), request.content(),
                request.occurredAt());
        MemoryService.MemoryDetail detail = memoryService.getDetail(userId, memoryId);
        return ApiResponse.success(MemoryResponse.from(
                detail.memory(),
                detail.messages(),
                detail.people(),
                faceService.listForMemory(userId, memoryId)));
    }

    @PostMapping("/{memoryId}/people")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LinkedPersonResponse> tagPerson(
            @PathVariable UUID memoryId, @Valid @RequestBody TagPersonRequest request) {
        Person person = memoryService.tagPerson(CurrentUser.requireId(), memoryId, request.displayName());
        return ApiResponse.success(LinkedPersonResponse.from(person));
    }

    @DeleteMapping("/{memoryId}/people/{personId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untagPerson(@PathVariable UUID memoryId, @PathVariable UUID personId) {
        memoryService.untagPerson(CurrentUser.requireId(), memoryId, personId);
    }

    @PostMapping("/{memoryId}/faces/{faceId}/confirm")
    public ApiResponse<FaceDetectionResponse> confirmFace(
            @PathVariable UUID memoryId,
            @PathVariable UUID faceId,
            @Valid @RequestBody ConfirmFaceRequest request) {
        request.requireIdentity();
        // memoryId kept in path for a stable URL shape; ownership is checked via face.userId.
        return ApiResponse.success(faceService.confirm(
                CurrentUser.requireId(), faceId, request.personId(), request.displayName()));
    }

    @DeleteMapping("/{memoryId}/faces/{faceId}")
    public ApiResponse<FaceDetectionResponse> clearFace(
            @PathVariable UUID memoryId, @PathVariable UUID faceId) {
        return ApiResponse.success(
                faceService.clearAssignment(CurrentUser.requireId(), memoryId, faceId));
    }

    @DeleteMapping("/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(@PathVariable UUID memoryId) {
        memoryService.delete(CurrentUser.requireId(), memoryId);
    }

    /**
     * Streams the stored file after checking ownership. Returns the raw bytes rather than the API
     * envelope, since the consumer is an {@code <img>} tag or a download, not a JSON parser.
     */
    @GetMapping("/{memoryId}/media/{assetId}")
    public ResponseEntity<Resource> downloadMedia(@PathVariable UUID memoryId, @PathVariable UUID assetId) {
        MediaAccessService.DownloadableMedia media = mediaAccessService.openOwnedAsset(CurrentUser.requireId(),
                memoryId, assetId);
        MediaAsset asset = media.asset();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .contentLength(asset.getSizeBytes())
                .eTag("\"%s\"".formatted(asset.getChecksum()))
                // Private: this is one person's photo, and no shared cache should ever hold a copy.
                // Immutable because stored objects are never rewritten in place.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.getFileName())
                        .build()
                        .toString())
                .body(media.resource());
    }
}
