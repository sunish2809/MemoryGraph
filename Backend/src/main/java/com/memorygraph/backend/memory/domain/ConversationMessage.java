package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line in a conversation memory. The parent {@link Memory} remains the day-bucket timeline /
 * search unit; these rows restore chat fidelity on the detail page.
 */
@Entity
@Table(name = "conversation_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "sender_name", nullable = false, length = 255, updatable = false)
    private String senderName;

    @Column(name = "body", nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Column(name = "sort_index", nullable = false, updatable = false)
    private int sortIndex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private ConversationMessage(UUID memoryId, UUID userId, Instant sentAt, String senderName, String body,
            int sortIndex) {
        this.id = UUID.randomUUID();
        this.memoryId = memoryId;
        this.userId = userId;
        this.sentAt = sentAt;
        this.senderName = senderName;
        this.body = body;
        this.sortIndex = sortIndex;
    }

    public static ConversationMessage of(UUID memoryId, UUID userId, Instant sentAt, String senderName, String body,
            int sortIndex) {
        return new ConversationMessage(memoryId, userId, sentAt, senderName, body == null ? "" : body, sortIndex);
    }
}
