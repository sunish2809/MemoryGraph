package com.memorygraph.backend.ai.rag;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.memory.domain.Memory;

/**
 * Answers without calling an LLM: lists the retrieved memories and lets the user follow the sources.
 * <p>
 * Always registered so Ask can fall back to it when OpenAI is configured but refuses (quota, outage).
 * When chat is {@code none} this is also the primary generator. Honest about its limits — it does
 * not paraphrase or synthesise.
 */
@Component
class RetrievalOnlyAnswerGenerator implements AnswerGenerator {

    private static final String UNCONFIGURED =
            "A language model is not configured on this server, so here is what they say:\n";
    private static final String UNAVAILABLE =
            "The language model could not write an answer (quota or a temporary error), "
                    + "so here is what the retrieved memories say:\n";

    @Override
    public GeneratedAnswer generate(String question, List<AskSource> sources) {
        return generate(sources, UNCONFIGURED);
    }

    /**
     * Same listing as {@link #generate}, but the intro tells the truth when a model is configured and
     * failed rather than claiming one was never set up.
     */
    GeneratedAnswer generateWhenUnavailable(List<AskSource> sources) {
        return generate(sources, UNAVAILABLE);
    }

    private GeneratedAnswer generate(List<AskSource> sources, String intro) {
        if (sources.isEmpty()) {
            return new GeneratedAnswer(
                    "I could not find anything in your memories that answers that. Try different words, "
                            + "or add the memory if it is not saved yet.",
                    false,
                    "retrieval-only");
        }

        StringBuilder answer = new StringBuilder();
        answer.append("I found ").append(sources.size())
                .append(sources.size() == 1 ? " memory" : " memories")
                .append(" that look relevant. ")
                .append(intro);

        int index = 1;
        for (AskSource source : sources) {
            Memory memory = source.memory();
            answer.append('\n').append(index++).append(". ");
            if (StringUtils.hasText(memory.getTitle())) {
                answer.append(memory.getTitle().strip());
            } else {
                answer.append("Untitled ").append(memory.getType().name().toLowerCase());
            }
            String excerpt = excerpt(memory);
            if (excerpt != null) {
                answer.append(" — ").append(excerpt);
            }
        }

        return new GeneratedAnswer(answer.toString(), true, "retrieval-only");
    }

    private static String excerpt(Memory memory) {
        String text = firstNonBlank(memory.getContent(), memory.getDescription());
        if (text == null) {
            return null;
        }
        String collapsed = text.replaceAll("\\s+", " ").strip();
        return collapsed.length() <= 180 ? collapsed : collapsed.substring(0, 180).stripTrailing() + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
