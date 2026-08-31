package com.memorygraph.backend.ai.rag;

import java.util.List;

/**
 * The outcome of asking a question: an answer, whether it was grounded in anything, and the enriched
 * sources offered as evidence (including chat lines and related photos).
 */
public record AskResult(
        String question,
        String answer,
        boolean grounded,
        String model,
        String notice,
        List<AskSource> sources) {
}
