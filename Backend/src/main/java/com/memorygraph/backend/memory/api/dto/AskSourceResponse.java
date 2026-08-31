package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.ai.rag.AskSource;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.ProcessingStatus;

/**
 * One Ask evidence item: summary fields plus chat lines and related photos so the client can render
 * conversations and image galleries without a second round-trip.
 */
public record AskSourceResponse(
        UUID id,
        MemoryType type,
        MemorySource source,
        String title,
        String description,
        String excerpt,
        Instant occurredAt,
        ProcessingStatus processingStatus,
        List<String> people,
        List<MediaAssetResponse> assets,
        List<ConversationMessageResponse> messages,
        List<RelatedPhotoResponse> relatedPhotos) {

    private static final int EXCERPT_LENGTH = 1200;

    public static AskSourceResponse from(AskSource source) {
        Memory memory = source.memory();
        return new AskSourceResponse(
                memory.getId(),
                memory.getType(),
                memory.getSource(),
                memory.getTitle(),
                memory.getDescription(),
                excerpt(memory),
                memory.getOccurredAt(),
                memory.getProcessingStatus(),
                source.peopleNames(),
                memory.getAssets().stream().map(asset -> MediaAssetResponse.from(asset, memory.getId())).toList(),
                source.messages().stream().map(ConversationMessageResponse::from).toList(),
                source.relatedPhotos().stream().map(RelatedPhotoResponse::from).toList());
    }

    private static String excerpt(Memory memory) {
        String text = memory.getContent();
        if (text == null || text.isBlank()) {
            text = memory.getDescription();
        }
        if (text == null) {
            return null;
        }
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }

    /** A compact photo linked to a conversation day (WhatsApp attachment, etc.). */
    public record RelatedPhotoResponse(
            UUID id,
            String title,
            String description,
            Instant occurredAt,
            List<MediaAssetResponse> assets) {

        public static RelatedPhotoResponse from(Memory photo) {
            return new RelatedPhotoResponse(
                    photo.getId(),
                    photo.getTitle(),
                    photo.getDescription(),
                    photo.getOccurredAt(),
                    photo.getAssets().stream()
                            .map(asset -> MediaAssetResponse.from(asset, photo.getId()))
                            .toList());
        }
    }
}
