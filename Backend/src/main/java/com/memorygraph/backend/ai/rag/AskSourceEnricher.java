package com.memorygraph.backend.ai.rag;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.ConversationMessageRepository;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryPersonLinkRepository;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemoryType;

import lombok.RequiredArgsConstructor;

/**
 * Turns retrieved memories into Ask sources: loads chat lines, people, and same-day photos that share
 * people with a WhatsApp conversation so the UI and the model can see both text and images.
 */
@Service
@RequiredArgsConstructor
public class AskSourceEnricher {

    private static final int MAX_RELATED_PHOTOS_PER_CONVERSATION = 12;
    private static final int MAX_EXTRA_PHOTO_SOURCES = 16;

    private final ConversationMessageRepository conversationMessages;
    private final MemoryPersonLinkRepository personLinks;
    private final MemoryRepository memories;

    @Transactional(readOnly = true)
    public List<AskSource> enrich(UUID userId, List<Memory> retrieved, ZoneId zone) {
        Set<UUID> knownIds = new HashSet<>();
        for (Memory memory : retrieved) {
            knownIds.add(memory.getId());
        }

        List<AskSource> sources = new ArrayList<>();
        Map<UUID, Memory> extrasToAppend = new LinkedHashMap<>();

        for (Memory memory : retrieved) {
            List<String> people = personLinks.findPersonNamesByMemoryId(memory.getId());
            List<ConversationMessage> messages = memory.getType() == MemoryType.CONVERSATION
                    ? conversationMessages.findByMemoryIdOrderBySortIndexAsc(memory.getId())
                    : List.of();
            List<Memory> relatedPhotos = memory.getType() == MemoryType.CONVERSATION
                    ? loadRelatedPhotos(userId, memory, zone)
                    : List.of();

            for (Memory photo : relatedPhotos) {
                if (!knownIds.contains(photo.getId()) && extrasToAppend.size() < MAX_EXTRA_PHOTO_SOURCES) {
                    extrasToAppend.put(photo.getId(), photo);
                    knownIds.add(photo.getId());
                }
            }

            sources.add(new AskSource(memory, messages, people, relatedPhotos));
        }

        for (Memory photo : extrasToAppend.values()) {
            sources.add(new AskSource(
                    photo,
                    List.of(),
                    personLinks.findPersonNamesByMemoryId(photo.getId()),
                    List.of()));
        }

        return sources;
    }

    private List<Memory> loadRelatedPhotos(UUID userId, Memory conversation, ZoneId zone) {
        List<UUID> personIds = personLinks.findPersonIdsByMemoryId(conversation.getId());
        LocalDate day = conversation.getOccurredAt().atZone(zone).toLocalDate();
        var from = day.atStartOfDay(zone).toInstant();
        var to = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<UUID> photoIds = personIds.isEmpty()
                ? List.of()
                : personLinks.findPhotoIdsSharingPeopleInWindow(userId, personIds, from, to);

        if (photoIds.isEmpty()) {
            String chatName = whatsAppChatName(conversation.getTitle());
            if (chatName != null) {
                photoIds = memories.findWhatsAppPhotoIdsForChatDay(
                        userId, MemoryType.PHOTO, "From WhatsApp · " + chatName, from, to);
            }
        }
        if (photoIds.isEmpty()) {
            return List.of();
        }

        List<UUID> limited = photoIds.size() > MAX_RELATED_PHOTOS_PER_CONVERSATION
                ? photoIds.subList(0, MAX_RELATED_PHOTOS_PER_CONVERSATION)
                : photoIds;

        Map<UUID, Memory> loaded = new LinkedHashMap<>();
        for (Memory photo : memories.findAllWithAssets(limited)) {
            loaded.put(photo.getId(), photo);
        }

        List<Memory> related = new ArrayList<>(limited.size());
        for (UUID id : limited) {
            Memory photo = loaded.get(id);
            if (photo != null) {
                related.add(photo);
            }
        }
        return related;
    }

    /** Titles look like {@code WhatsApp · Raj Tilak · 2024-05-17}. */
    private static String whatsAppChatName(String title) {
        if (title == null || !title.startsWith("WhatsApp · ")) {
            return null;
        }
        String rest = title.substring("WhatsApp · ".length());
        int lastSep = rest.lastIndexOf(" · ");
        if (lastSep <= 0) {
            return null;
        }
        String name = rest.substring(0, lastSep).strip();
        return name.isEmpty() ? null : name;
    }
}
