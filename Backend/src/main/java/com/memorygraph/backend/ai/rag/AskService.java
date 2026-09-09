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
    private final AskQuotaService quota;

    @Transactional(readOnly = true)
    public AskResult ask(
            UUID userId, String question, List<MemoryType> types, LocalDayRange range, ZoneId zone) {
        return ask(userId, question, types, range, zone, List.of());
    }

    @Transactional(readOnly = true)
    public AskResult ask(
            UUID userId,
            String question,
            List<MemoryType> types,
            LocalDayRange range,
            ZoneId zone,
            List<AskConversationTurn> history) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String normalised = question.strip();
        List<AskConversationTurn> prior = history == null ? List.of() : List.copyOf(history);

        SearchCriteria criteria = SearchCriteria.of(
                userId, retrievalQuery(normalised, prior), types, range, SearchSort.RELEVANCE);
        List<Memory> retrieved = retriever.retrieve(criteria);
        List<AskSource> sources = enricher.enrich(userId, retrieved, zone);

        log.info("Ask for user {} retrieved {} source(s) ({} after enrich) for question length {}",
                userId, retrieved.size(), sources.size(), normalised.length());

        if (!quota.hasAllowance(userId)) {
            AnswerGenerator.GeneratedAnswer fallback = retrievalOnly.generateWhenQuotaExceeded(sources);
            return new AskResult(
                    normalised,
                    fallback.answer(),
                    fallback.grounded(),
                    fallback.model(),
                    quotaNotice(),
                    sources);
        }

        try {
            AnswerGenerator.GeneratedAnswer generated = answers.generate(userId, normalised, sources, prior);
            return new AskResult(
                    normalised, generated.answer(), generated.grounded(), generated.model(), null, sources);
        } catch (AskQuotaExceededException ex) {
            AnswerGenerator.GeneratedAnswer fallback = retrievalOnly.generateWhenQuotaExceeded(sources);
            return new AskResult(
                    normalised,
                    fallback.answer(),
                    fallback.grounded(),
                    fallback.model(),
                    quotaNotice(),
                    sources);
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

    /**
     * Follow-ups like "the cheaper one" have no place names. Fold recent user questions into retrieval
     * so the same trip memories still surface.
     */
    static String retrievalQuery(String question, List<AskConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        String priorUsers = history.stream()
                .filter(AskConversationTurn::isUser)
                .map(AskConversationTurn::content)
                .filter(StringUtils::hasText)
                .map(String::strip)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        if (priorUsers.isEmpty()) {
            return question;
        }
        String combined = priorUsers + " " + question;
        return combined.length() <= 1000 ? combined : combined.substring(combined.length() - 1000);
    }

    private static String quotaNotice() {
        return "You've used this month's AI answers on this account. Matching memories are listed instead.";
    }

    private static String notice(AnswerGenerationException ex) {
        String detail = ex.getMessage();
        if (detail != null && (detail.toLowerCase().contains("quota") || detail.toLowerCase().contains("billing"))) {
            return "OpenAI rejected the request (quota or billing). Showing the memories that matched instead.";
        }
        return "The language model could not answer just now. Showing the memories that matched instead.";
    }
}
