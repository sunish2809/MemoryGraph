package com.memorygraph.backend.memory.application.imports.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WhatsAppChatParserTest {

    private final WhatsAppChatParser parser = new WhatsAppChatParser();

    @Test
    void parsesBracketedMultilineAndSkipsSystemNotice() throws IOException {
        String text = fixture("fixtures/whatsapp/sample-chat.txt");
        WhatsAppChatParser.ParsedChat chat = parser.parse(text, "Rahul");

        assertThat(chat.chatName()).isEqualTo("Rahul");
        assertThat(chat.messages()).hasSize(5);
        assertThat(chat.messages().getFirst().sender()).isEqualTo("Rahul");
        assertThat(chat.messages().getFirst().body()).contains("Sikkim");
        assertThat(chat.messages().get(1).body()).contains("Book the train");
        assertThat(chat.messages().get(2).attachments()).containsExactly("IMG-20240815-WA0001.jpg");
        assertThat(chat.messages().get(3).when()).isEqualTo(LocalDateTime.of(2024, 8, 16, 10, 0));
    }

    @Test
    void parsesUsStyleTwelveHourTimestamps() throws IOException {
        WhatsAppChatParser.ParsedChat chat = parser.parse(fixture("fixtures/whatsapp/sample-us.txt"), "Chat");
        assertThat(chat.messages()).hasSize(2);
        assertThat(chat.messages().getFirst().when()).isEqualTo(LocalDateTime.of(2024, 8, 15, 9, 16, 1));
    }

    @Test
    void parsesDashedAndroidFormat() throws IOException {
        WhatsAppChatParser.ParsedChat chat = parser.parse(fixture("fixtures/whatsapp/sample-dashed.txt"), "Chat");
        assertThat(chat.messages()).hasSize(2);
        assertThat(chat.messages().getFirst().sender()).isEqualTo("Rahul");
    }

    @Test
    void parsesGermanDottedDatesAndInvisibleLtrMarks() {
        String text = """
                \u200e[15.08.2024, 14:30:00] Rahul: Hallo
                \u200e[15.08.2024, 14:31:00] You: Hi
                """;
        WhatsAppChatParser.ParsedChat chat = parser.parse(text, "Rahul");
        assertThat(chat.messages()).hasSize(2);
        assertThat(chat.messages().getFirst().when()).isEqualTo(LocalDateTime.of(2024, 8, 15, 14, 30));
    }

    @Test
    void parsesAndroidWithDotTimesAndAmPmDots() {
        String text = """
                15/08/2024, 2.30 p.m. - Rahul: Afternoon
                15/08/2024, 2.31 p.m. - You: Yes
                """;
        WhatsAppChatParser.ParsedChat chat = parser.parse(text, "Chat");
        assertThat(chat.messages()).hasSize(2);
        assertThat(chat.messages().getFirst().when()).isEqualTo(LocalDateTime.of(2024, 8, 15, 14, 30));
    }

    @Test
    void derivesChatNameFromExportFileName() {
        assertThat(WhatsAppChatParser.chatNameFromFileName("WhatsApp Chat with Rahul.txt")).isEqualTo("Rahul");
        assertThat(WhatsAppChatParser.chatNameFromFileName("_chat.txt")).isEqualTo("WhatsApp chat");
    }

    private static String fixture(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
