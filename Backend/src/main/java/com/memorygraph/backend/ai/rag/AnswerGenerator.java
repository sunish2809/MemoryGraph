package com.memorygraph.backend.ai.rag;

import java.util.List;

/**
 * Produces an answer grounded in a retrieved set of Ask sources.
 * <p>
 * Implementations must not invent facts outside that set. When the set is empty they must say so
 * plainly rather than guessing.
 */
public interface AnswerGenerator {

    GeneratedAnswer generate(String question, List<AskSource> sources);

    record GeneratedAnswer(String answer, boolean grounded, String model) {
    }
}
