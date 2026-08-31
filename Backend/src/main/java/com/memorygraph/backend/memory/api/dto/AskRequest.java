package com.memorygraph.backend.memory.api.dto;

import java.util.List;

import com.memorygraph.backend.ai.rag.AskResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /ask}. */
public record AskRequest(
        @NotBlank @Size(max = 1000) String question,
        /** Optional inclusive first day, interpreted in the request's zone. */
        java.time.LocalDate from,
        /** Optional inclusive last day, interpreted in the request's zone. */
        java.time.LocalDate to,
        /** IANA timezone for {@code from}/{@code to}. Defaults to UTC when omitted. */
        String zone,
        /** Optional type filter; omitted means every type. */
        List<com.memorygraph.backend.memory.domain.MemoryType> type) {
}
