package com.memorygraph.backend.memory.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.memory.api.dto.CreateTripRequest;
import com.memorygraph.backend.memory.api.dto.TripDetailResponse;
import com.memorygraph.backend.memory.api.dto.TripsPageResponse;
import com.memorygraph.backend.memory.api.dto.UpdateTripRequest;
import com.memorygraph.backend.memory.application.TripService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/trips")
@Validated
@RequiredArgsConstructor
public class TripsController {

    private final TripService trips;

    @GetMapping
    public ApiResponse<TripsPageResponse> list() {
        return ApiResponse.success(trips.list(CurrentUser.requireId()));
    }

    @PostMapping
    public ApiResponse<TripDetailResponse> create(@Valid @RequestBody CreateTripRequest request) {
        return ApiResponse.success(trips.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> get(@PathVariable UUID tripId) {
        return ApiResponse.success(trips.get(CurrentUser.requireId(), tripId));
    }

    @PatchMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> update(
            @PathVariable UUID tripId, @Valid @RequestBody UpdateTripRequest request) {
        return ApiResponse.success(trips.update(CurrentUser.requireId(), tripId, request));
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID tripId) {
        trips.delete(CurrentUser.requireId(), tripId);
    }
}
