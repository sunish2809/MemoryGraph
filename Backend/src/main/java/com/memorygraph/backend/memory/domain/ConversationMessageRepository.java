package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findByMemoryIdOrderBySortIndexAsc(UUID memoryId);

    List<ConversationMessage> findByUserIdOrderByMemoryIdAscSortIndexAsc(UUID userId);
}
