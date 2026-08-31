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
import com.memorygraph.backend.memory.api.dto.MergePeopleRequest;
import com.memorygraph.backend.memory.api.dto.PeopleGraphResponse;
import com.memorygraph.backend.memory.api.dto.PersonDetailResponse;
import com.memorygraph.backend.memory.api.dto.PersonSummaryResponse;
import com.memorygraph.backend.memory.api.dto.RenamePersonRequest;
import com.memorygraph.backend.memory.application.PeopleGraphService;
import com.memorygraph.backend.memory.application.PersonService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiPaths.V1 + "/people")
@Validated
@RequiredArgsConstructor
public class PeopleController {

    private final PersonService people;
    private final PeopleGraphService graph;

    @GetMapping
    public ApiResponse<List<PersonSummaryResponse>> list() {
        return ApiResponse.success(people.list(CurrentUser.requireId()));
    }

    @GetMapping("/graph")
    public ApiResponse<PeopleGraphResponse> peopleGraph() {
        return ApiResponse.success(graph.graph(CurrentUser.requireId()));
    }

    @GetMapping("/{personId}")
    public ApiResponse<PersonDetailResponse> get(@PathVariable UUID personId) {
        return ApiResponse.success(people.get(CurrentUser.requireId(), personId));
    }

    @PatchMapping("/{personId}")
    public ApiResponse<PersonDetailResponse> rename(
            @PathVariable UUID personId, @Valid @RequestBody RenamePersonRequest request) {
        return ApiResponse.success(people.rename(CurrentUser.requireId(), personId, request.displayName()));
    }

    @PostMapping("/{personId}/merge")
    public ApiResponse<PersonDetailResponse> merge(
            @PathVariable UUID personId, @Valid @RequestBody MergePeopleRequest request) {
        return ApiResponse.success(
                people.merge(CurrentUser.requireId(), personId, request.sourcePersonId()));
    }
}
