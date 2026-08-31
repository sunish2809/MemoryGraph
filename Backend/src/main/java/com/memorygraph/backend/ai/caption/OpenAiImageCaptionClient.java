package com.memorygraph.backend.ai.caption;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
@RequiredArgsConstructor
class OpenAiImageCaptionClient implements ImageCaptionClient {

    private static final String PROMPT = """
            Describe this personal photo in one or two short sentences for a searchable life archive.
            Mention places, activities and notable objects when visible. Do not invent names of people.
            """;

    private final ChatModel chatModel;

    @Override
    public Optional<String> caption(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        try {
            MimeType mime = MimeTypeUtils.parseMimeType(
                    StringUtils.hasText(mimeType) ? mimeType : "image/jpeg");
            Media media = Media.builder().mimeType(mime).data(imageBytes).build();
            UserMessage message = UserMessage.builder().text(PROMPT).media(media).build();
            ChatResponse response = chatModel.call(new Prompt(List.of(message)));
            String text = response.getResult().getOutput().getText();
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }
            return Optional.of(text.strip());
        } catch (Exception ex) {
            log.warn("Image caption failed: {}", ex.toString());
            return Optional.empty();
        }
    }
}
