package com.memorygraph.backend.memory.api;

import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.memory.api.dto.ConfirmFaceRequest;
import com.memorygraph.backend.memory.api.dto.FaceDetectionResponse;
import com.memorygraph.backend.memory.api.dto.FaceReviewResponse;
import com.memorygraph.backend.memory.application.face.FaceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/faces")
@Validated
@RequiredArgsConstructor
public class FacesController {

    private final FaceService faces;

    @GetMapping("/review")
    public ApiResponse<FaceReviewResponse> review() {
        return ApiResponse.success(faces.review(CurrentUser.requireId()));
    }

    @PostMapping("/{faceId}/reject-suggestion")
    public ApiResponse<FaceDetectionResponse> rejectSuggestion(@PathVariable UUID faceId) {
        return ApiResponse.success(faces.rejectSuggestion(CurrentUser.requireId(), faceId));
    }

    @PostMapping("/{faceId}/ignore")
    public ApiResponse<FaceDetectionResponse> ignore(@PathVariable UUID faceId) {
        return ApiResponse.success(faces.ignore(CurrentUser.requireId(), faceId));
    }

    @PostMapping("/{faceId}/restore")
    public ApiResponse<FaceDetectionResponse> restore(@PathVariable UUID faceId) {
        return ApiResponse.success(faces.restore(CurrentUser.requireId(), faceId));
    }

    @PostMapping("/clusters/{clusterId}/confirm")
    public ApiResponse<FaceReviewResponse> confirmCluster(
            @PathVariable UUID clusterId, @Valid @RequestBody ConfirmFaceRequest request) {
        request.requireIdentity();
        return ApiResponse.success(faces.confirmCluster(
                CurrentUser.requireId(), clusterId, request.personId(), request.displayName()));
    }

    @PostMapping("/clusters/{clusterId}/ignore")
    public ApiResponse<FaceReviewResponse> ignoreCluster(@PathVariable UUID clusterId) {
        return ApiResponse.success(faces.ignoreCluster(CurrentUser.requireId(), clusterId));
    }
}
