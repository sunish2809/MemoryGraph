package com.memorygraph.backend.ai.rag;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.search.SearchCriteria;
import com.memorygraph.backend.memory.search.SearchSort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retrieve, enrich with chat lines and related photos, then answer.
 * <p>
 * Retrieval allows semantic-only hits (paraphrases); generation is grounded on that set alone —
 * never the whole archive — which is what keeps the LLM from inventing a past the user never recorded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskService {

    private final AskMemoryRetriever retriever;
    private final AskSourceEnricher enricher;
    private final AnswerGenerator answers;
    private final RetrievalOnlyAnswerGenerator retrievalOnly;

    @Transactional(readOnly = true)
    public AskResult ask(
            UUID userId, String question, List<MemoryType> types, LocalDayRange range, ZoneId zone) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String normalised = question.strip();

        SearchCriteria criteria = SearchCriteria.of(userId, normalised, types, range, SearchSort.RELEVANCE);
        List<Memory> retrieved = retriever.retrieve(criteria);
        List<AskSource> sources = enricher.enrich(userId, retrieved, zone);

        log.info("Ask for user {} retrieved {} source(s) ({} after enrich) for question length {}",
                userId, retrieved.size(), sources.size(), normalised.length());

        try {
            AnswerGenerator.GeneratedAnswer generated = answers.generate(normalised, sources);
            return new AskResult(
                    normalised, generated.answer(), generated.grounded(), generated.model(), null, sources);
        } catch (AnswerGenerationException ex) {
            log.warn("Ask generation failed; falling back to retrieval-only: {}", ex.getMessage());
            AnswerGenerator.GeneratedAnswer fallback = retrievalOnly.generateWhenUnavailable(sources);
            return new AskResult(
                    normalised,
                    fallback.answer(),
                    fallback.grounded(),
                    fallback.model(),
                    notice(ex),
                    sources);
        }
    }

    private static String notice(AnswerGenerationException ex) {
        String detail = ex.getMessage();
        if (detail != null && (detail.toLowerCase().contains("quota") || detail.toLowerCase().contains("billing"))) {
            return "OpenAI rejected the request (quota or billing). Showing the memories that matched instead.";
        }
        return "The language model could not answer just now. Showing the memories that matched instead.";
    }
}
