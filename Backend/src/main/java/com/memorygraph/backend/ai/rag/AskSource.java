package com.memorygraph.backend.ai.rag;

import java.util.List;

import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.Memory;

/**
 * One retrieved memory prepared for Ask: transcript lines, people, and related chat-day photos when
 * this is a WhatsApp conversation bucket.
 */
public record AskSource(
        Memory memory,
        List<ConversationMessage> messages,
        List<String> peopleNames,
        List<Memory> relatedPhotos) {

    public static AskSource of(Memory memory) {
        return new AskSource(memory, List.of(), List.of(), List.of());
    }
}
