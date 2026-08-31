package com.memorygraph.backend.ai.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.ai.AiProperties;
import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asks an LLM to answer using only the supplied memories as evidence.
 * <p>
 * The system prompt is deliberately strict: confident fabrication is the failure mode this product
 * can least afford. When the evidence is thin the model is told to say so, and the response is still
 * paired with the source list the caller retrieved — never with memories the model invents.
 * <p>
 * Gated on {@code spring.ai.model.chat=openai} (not {@code @ConditionalOnBean(ChatModel)}) because
 * component-scan conditions run before Spring AI auto-config registers the model bean.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
@RequiredArgsConstructor
class OpenAiGroundedAnswerGenerator implements AnswerGenerator {

    private static final int MAX_SOURCE_CHARS = 8000;
    private static final int MAX_MESSAGES = 80;

    private static final String SYSTEM = """
            You answer questions about the user's own life using only the memories provided below.
            Rules:
            - Use only facts that appear in the memories. Do not invent dates, people, places or events.
            - Prefer concrete details: quote short chat lines when useful, name people, and mention photos
              when a caption or description is present. Hinglish or Hindi text may appear — quote it as-is.
            - If the memories are not enough to answer, say so plainly and suggest what kind of memory would help.
            - Prefer short, direct answers. Cite memories by their bracketed number, e.g. [1], when you draw on them.
            - Never claim access to anything outside the provided memories.
            """;

    private final ChatModel chatModel;
    private final AiProperties properties;

    @Override
    public GeneratedAnswer generate(String question, List<AskSource> sources) {
        if (sources.isEmpty()) {
            return new GeneratedAnswer(
                    "I could not find anything in your memories that answers that. Try different words, "
                            + "or add the memory if it is not saved yet.",
                    false,
                    properties.chatModel());
        }

        String context = IntStream.range(0, sources.size())
                .mapToObj(i -> formatSource(i + 1, sources.get(i)))
                .collect(Collectors.joining("\n\n"));

        String user = "Memories:\n" + context + "\n\nQuestion: " + question;

        try {
            String answer = chatModel.call(new Prompt(List.of(
                    new SystemMessage(SYSTEM),
                    new UserMessage(user)))).getResult().getOutput().getText();
            if (!StringUtils.hasText(answer)) {
                return new GeneratedAnswer(
                        "The model returned an empty answer. The memories above are still listed as sources.",
                        true,
                        properties.chatModel());
            }
            return new GeneratedAnswer(answer.strip(), true, properties.chatModel());
        } catch (Exception ex) {
            log.error("Grounded answer generation failed", ex);
            String detail = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("quota")
                    ? "OpenAI rejected the request (quota or billing). Check your API plan, then try Ask again."
                    : "The answer could not be generated right now";
            throw new AnswerGenerationException(detail, ex);
        }
    }

    private static String formatSource(int index, AskSource source) {
        Memory memory = source.memory();
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(index).append("] ");
        builder.append(memory.getType().name()).append(" · ");
        if (StringUtils.hasText(memory.getTitle())) {
            builder.append(memory.getTitle().strip());
        } else {
            builder.append("Untitled");
        }
        builder.append(" (").append(memory.getOccurredAt()).append(')');

        if (!source.peopleNames().isEmpty()) {
            builder.append("\nPeople: ").append(String.join(", ", source.peopleNames()));
        }

        if (StringUtils.hasText(memory.getDescription())
                && memory.getType() != MemoryType.CONVERSATION) {
            builder.append('\n').append(truncate(memory.getDescription(), 800));
        }

        if (!source.messages().isEmpty()) {
            builder.append("\nChat:");
            List<ConversationMessage> messages = source.messages().size() > MAX_MESSAGES
                    ? source.messages().subList(0, MAX_MESSAGES)
                    : source.messages();
            StringBuilder chat = new StringBuilder();
            for (ConversationMessage message : messages) {
                chat.append('\n')
                        .append(message.getSenderName())
                        .append(": ")
                        .append(message.getBody());
            }
            if (source.messages().size() > MAX_MESSAGES) {
                chat.append("\n… (").append(source.messages().size() - MAX_MESSAGES)
                        .append(" more messages)");
            }
            builder.append(truncate(chat.toString(), MAX_SOURCE_CHARS));
        } else if (StringUtils.hasText(memory.getContent())) {
            builder.append('\n').append(truncate(memory.getContent(), MAX_SOURCE_CHARS));
        }

        if (!source.relatedPhotos().isEmpty()) {
            builder.append("\nRelated photos from this day:");
            for (Memory photo : source.relatedPhotos()) {
                builder.append("\n- ");
                if (StringUtils.hasText(photo.getTitle())) {
                    builder.append(photo.getTitle().strip());
                } else {
                    builder.append("photo");
                }
                String caption = firstNonBlank(photo.getContent(), photo.getDescription());
                if (caption != null) {
                    builder.append(": ").append(truncate(caption, 240));
                }
            }
        }

        return builder.toString();
    }

    private static String truncate(String text, int max) {
        String collapsed = text.strip();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…";
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
