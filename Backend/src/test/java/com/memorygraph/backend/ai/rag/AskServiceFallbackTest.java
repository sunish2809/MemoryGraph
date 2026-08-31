package com.memorygraph.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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
    private Memory memory;

    @Test
    void fallsBackToRetrievedMemoriesWhenTheLanguageModelFails() {
        when(memory.getTitle()).thenReturn("Sikkim trip");
        when(memory.getContent()).thenReturn("The train was delayed.");

        AskSource source = AskSource.of(memory);
        when(retriever.retrieve(any(SearchCriteria.class))).thenReturn(List.of(memory));
        when(enricher.enrich(any(), anyList(), any())).thenReturn(List.of(source));
        when(answers.generate(anyString(), anyList()))
                .thenThrow(new AnswerGenerationException(
                        "OpenAI rejected the request (quota or billing). Check your API plan, then try Ask again.",
                        null));

        AskService service = new AskService(retriever, enricher, answers, new RetrievalOnlyAnswerGenerator());
        AskResult result = service.ask(
                UUID.randomUUID(),
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
}
