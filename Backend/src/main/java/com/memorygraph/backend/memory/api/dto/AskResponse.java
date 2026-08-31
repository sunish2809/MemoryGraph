package com.memorygraph.backend.memory.api.dto;

import java.util.List;

import com.memorygraph.backend.ai.rag.AskResult;

/**
 * An answer plus the memories it was grounded in, with chat lines and related photos for rich
 * rendering in the Ask UI.
 */
public record AskResponse(
        String question,
        String answer,
        boolean grounded,
        String model,
        String notice,
        List<AskSourceResponse> sources) {

    public static AskResponse from(AskResult result) {
        return new AskResponse(
                result.question(),
                result.answer(),
                result.grounded(),
                result.model(),
                result.notice(),
                result.sources().stream().map(AskSourceResponse::from).toList());
    }
}
