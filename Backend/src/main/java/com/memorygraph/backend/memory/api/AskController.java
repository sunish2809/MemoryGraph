package com.memorygraph.backend.memory.api;

import java.time.ZoneId;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memorygraph.backend.ai.rag.AskConversationTurn;
import com.memorygraph.backend.ai.rag.AskQuotaService;
import com.memorygraph.backend.ai.rag.AskService;
import com.memorygraph.backend.auth.security.CurrentUser;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.api.ApiResponse;
import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.memory.api.dto.AskQuotaResponse;
import com.memorygraph.backend.memory.api.dto.AskRequest;
import com.memorygraph.backend.memory.api.dto.AskResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Ask: a natural-language question answered from the caller's own memories, with the sources listed.
 * <p>
 * Retrieval is hybrid search; generation is grounded on that result set alone. The response always
 * includes the sources that were considered, even when the answer is "I don't know" — so the user
 * can see what evidence was available.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/ask")
@Validated
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;
    private final AskQuotaService askQuota;

    @GetMapping("/quota")
    public ApiResponse<AskQuotaResponse> quota() {
        return ApiResponse.success(AskQuotaResponse.from(askQuota.status(CurrentUser.requireId())));
    }

    @PostMapping
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        ZoneId zone = ViewerZone.parse(request.zone() == null || request.zone().isBlank() ? "UTC" : request.zone());
        LocalDayRange range = LocalDayRange.of(request.from(), request.to(), zone);
        var userId = CurrentUser.requireId();

        return ApiResponse.success(AskResponse.from(
                askService.ask(userId, request.question(), request.type(), range, zone, toTurns(request.history())),
                askQuota.status(userId)));
    }

    private static List<AskConversationTurn> toTurns(
            List<com.memorygraph.backend.memory.api.dto.AskHistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .map(turn -> new AskConversationTurn(turn.role(), turn.content()))
                .toList();
    }
}
