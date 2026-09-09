package com.memorygraph.backend.memory.api.dto;

import java.time.LocalDate;

import com.memorygraph.backend.ai.rag.AskQuotaStatus;

/** Language-model Ask allowance for this account in the current UTC month. */
public record AskQuotaResponse(
        boolean capped,
        int limit,
        int used,
        int remaining,
        LocalDate periodStart,
        LocalDate resetsOn) {

    public static AskQuotaResponse from(AskQuotaStatus status) {
        return new AskQuotaResponse(
                status.capped(),
                status.limit(),
                status.used(),
                status.remaining(),
                status.periodStart(),
                status.resetsOn());
    }
}
