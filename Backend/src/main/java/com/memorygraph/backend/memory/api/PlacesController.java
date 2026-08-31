package com.memorygraph.backend.memory.api;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.memory.api.dto.MergePlaceRequest;
import com.memorygraph.backend.memory.api.dto.PlaceDetailResponse;
import com.memorygraph.backend.memory.api.dto.PlaceSummaryResponse;
import com.memorygraph.backend.memory.api.dto.RenamePlaceRequest;
import com.memorygraph.backend.memory.application.PlaceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/places")
@Validated
@RequiredArgsConstructor
public class PlacesController {

    private final PlaceService places;

    @GetMapping
    public ApiResponse<List<PlaceSummaryResponse>> list() {
        return ApiResponse.success(places.list(CurrentUser.requireId()));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> get(@PathVariable UUID placeId) {
        return ApiResponse.success(places.get(CurrentUser.requireId(), placeId));
    }

    @PatchMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> rename(
            @PathVariable UUID placeId, @Valid @RequestBody RenamePlaceRequest request) {
        return ApiResponse.success(places.rename(CurrentUser.requireId(), placeId, request.displayName()));
    }

    @PostMapping("/{placeId}/merge")
    public ApiResponse<PlaceDetailResponse> merge(
            @PathVariable UUID placeId, @Valid @RequestBody MergePlaceRequest request) {
        return ApiResponse.success(places.merge(CurrentUser.requireId(), placeId, request.sourcePlaceId()));
    }
}
