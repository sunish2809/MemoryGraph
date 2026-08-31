package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.ProcessingStatus;

/** One memory in full, for the detail view. */
public record MemoryResponse(
        UUID id,
        MemoryType type,
        MemorySource source,
        String title,
        String description,
        String content,
        Instant occurredAt,
        ProcessingStatus processingStatus,
        Instant createdAt,
        Instant updatedAt,
        List<MediaAssetResponse> assets,
        List<ConversationMessageResponse> messages,
        List<LinkedPersonResponse> people,
        List<FaceDetectionResponse> faces) {

    public static MemoryResponse from(Memory memory) {
        return from(memory, List.of(), List.of(), List.of());
    }

    public static MemoryResponse from(Memory memory, List<ConversationMessage> messages) {
        return from(memory, messages, List.of(), List.of());
    }

    public static MemoryResponse from(
            Memory memory,
            List<ConversationMessage> messages,
            List<Person> people,
            List<FaceDetectionResponse> faces) {
        return new MemoryResponse(
                memory.getId(),
                memory.getType(),
                memory.getSource(),
                memory.getTitle(),
                memory.getDescription(),
                memory.getContent(),
                memory.getOccurredAt(),
                memory.getProcessingStatus(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getAssets().stream().map(asset -> MediaAssetResponse.from(asset, memory.getId())).toList(),
                messages.stream().map(ConversationMessageResponse::from).toList(),
                people.stream().map(LinkedPersonResponse::from).toList(),
                faces);
    }
}
