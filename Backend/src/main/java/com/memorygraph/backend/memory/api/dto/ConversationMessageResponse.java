package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.ConversationMessage;

public record ConversationMessageResponse(
        UUID id,
        Instant sentAt,
        String senderName,
        String body,
        int sortIndex) {

    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getSentAt(),
                message.getSenderName(),
                message.getBody(),
                message.getSortIndex());
    }
}
