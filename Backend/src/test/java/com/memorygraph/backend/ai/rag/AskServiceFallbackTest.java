package com.memorygraph.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.search.SearchCriteria;

@ExtendWith(MockitoExtension.class)
class AskServiceFallbackTest {

    @Mock
    private AskMemoryRetriever retriever;

    @Mock
    private AskSourceEnricher enricher;

    @Mock
    private AnswerGenerator answers;

    @Mock
    private AskQuotaService quota;

    @Mock
    private Memory memory;

    private AskService service;

    @BeforeEach
    void createService() {
        service = new AskService(retriever, enricher, answers, new RetrievalOnlyAnswerGenerator(), quota);
    }

    @Test
    void fallsBackToRetrievedMemoriesWhenTheLanguageModelFails() {
        UUID userId = UUID.randomUUID();
        when(quota.hasAllowance(userId)).thenReturn(true);
        when(memory.getTitle()).thenReturn("Sikkim trip");
        when(memory.getContent()).thenReturn("The train was delayed.");

        AskSource source = AskSource.of(memory);
        when(retriever.retrieve(any(SearchCriteria.class))).thenReturn(List.of(memory));
        when(enricher.enrich(any(), anyList(), any())).thenReturn(List.of(source));
        when(answers.generate(eq(userId), anyString(), anyList()))
                .thenThrow(new AnswerGenerationException(
                        "OpenAI rejected the request (quota or billing). Check your API plan, then try Ask again.",
                        null));

        AskResult result = service.ask(
                userId,
                "What happened in Sikkim?",
                List.of(),
                LocalDayRange.of(null, null, ZoneOffset.UTC),
                ZoneOffset.UTC);

        assertThat(result.model()).isEqualTo("retrieval-only");
        assertThat(result.notice()).contains("quota");
        assertThat(result.answer()).contains("Sikkim trip");
        assertThat(result.answer()).contains("could not write an answer");
        assertThat(result.answer()).doesNotContain("is not configured");
    }

    @Test
    void listsMemoriesWithoutCallingTheModelWhenTheMonthlyCapIsReached() {
        UUID userId = UUID.randomUUID();
        when(quota.hasAllowance(userId)).thenReturn(false);
        when(memory.getTitle()).thenReturn("Sikkim trip");
        when(memory.getContent()).thenReturn("The train was delayed.");

        AskSource source = AskSource.of(memory);
        when(retriever.retrieve(any(SearchCriteria.class))).thenReturn(List.of(memory));
        when(enricher.enrich(any(), anyList(), any())).thenReturn(List.of(source));

        AskResult result = service.ask(
                userId,
                "What happened in Sikkim?",
                List.of(),
                LocalDayRange.of(null, null, ZoneOffset.UTC),
                ZoneOffset.UTC);

        verify(answers, never()).generate(any(), anyString(), anyList());
        assertThat(result.model()).isEqualTo("retrieval-only");
        assertThat(result.notice()).contains("this month");
        assertThat(result.answer()).contains("Sikkim trip");
        assertThat(result.answer()).contains("monthly AI answers");
    }
}
