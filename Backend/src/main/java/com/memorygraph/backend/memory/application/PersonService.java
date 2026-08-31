package com.memorygraph.backend.memory.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.api.dto.MemorySummaryResponse;
import com.memorygraph.backend.memory.api.dto.PersonDetailResponse;
import com.memorygraph.backend.memory.api.dto.PersonDetailResponse.ConnectedCounts;
import com.memorygraph.backend.memory.api.dto.PersonSummaryResponse;
import com.memorygraph.backend.memory.domain.FaceDetectionRepository;
import com.memorygraph.backend.memory.domain.FaceSuggestionRejectionRepository;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryPersonLinkRepository;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PersonService {

    private static final int RECENT_MEMORIES = 50;
    private static final int PHOTO_GALLERY = 120;

    private final PersonRepository people;
    private final MemoryPersonLinkRepository links;
    private final MemoryRepository memories;
    private final FaceDetectionRepository faces;
    private final FaceSuggestionRejectionRepository rejections;

    @Transactional(readOnly = true)
    public List<PersonSummaryResponse> list(UUID userId) {
        return people.findAllForUser(userId).stream()
                .map(person -> PersonSummaryResponse.from(person,
                        people.countMemoriesForPerson(userId, person.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonDetailResponse get(UUID userId, UUID personId) {
        Person person = requireOwned(userId, personId);
        long count = people.countMemoriesForPerson(userId, personId);
        ConnectedCounts connected = connectedCounts(userId, personId);
        List<UUID> photoIds = links.findPhotoMemoryIdsByUserAndPerson(
                userId, personId, PageRequest.of(0, PHOTO_GALLERY));
        List<MemorySummaryResponse> photos = hydrateOrdered(photoIds).stream()
                .map(MemorySummaryResponse::from)
                .toList();
        List<UUID> ids = links.findMemoryIdsByUserAndPerson(userId, personId,
                PageRequest.of(0, RECENT_MEMORIES));
        List<MemorySummaryResponse> recent = hydrateOrdered(ids).stream()
                .filter(memory -> memory.getType() != MemoryType.PHOTO)
                .map(MemorySummaryResponse::from)
                .toList();
        return PersonDetailResponse.from(person, count, connected, recent, photos);
    }

    @Transactional
    public PersonDetailResponse rename(UUID userId, UUID personId, String displayName) {
        Person person = requireOwned(userId, personId);
        String normalized = Person.normalise(displayName);
        if (!normalized.equals(person.getNormalizedName())) {
            people.findByUserIdAndNormalizedName(userId, normalized)
                    .filter(other -> !other.getId().equals(personId))
                    .ifPresent(other -> {
                        throw new ApiException(ErrorCode.NAME_ALREADY_TAKEN,
                                "Someone is already named \"" + other.getDisplayName()
                                        + "\". Merge the two people instead of renaming.");
                    });
        }
        person.rename(displayName);
        return get(userId, personId);
    }

    /**
     * Moves every memory link and face assignment from {@code sourcePersonId} onto {@code keepId},
     * then deletes the duplicate. WhatsApp senders and face tags often create both "Raj" and
     * "Raj Sharma"; this is how they become one person.
     */
    @Transactional
    public PersonDetailResponse merge(UUID userId, UUID keepId, UUID sourcePersonId) {
        if (keepId.equals(sourcePersonId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cannot merge a person into themselves");
        }
        requireOwned(userId, keepId);
        Person source = requireOwned(userId, sourcePersonId);
        links.copyLinksToKeep(keepId, sourcePersonId);
        links.deleteByPersonId(sourcePersonId);
        faces.reassignNamedPerson(keepId, sourcePersonId);
        faces.reassignSuggestedPerson(keepId, sourcePersonId);
        rejections.deleteSourceDuplicates(keepId, sourcePersonId);
        rejections.reassignPerson(keepId, sourcePersonId);
        people.delete(source);
        people.flush();
        return get(userId, keepId);
    }

    private Person requireOwned(UUID userId, UUID personId) {
        return people.findByIdAndUserId(personId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Person", personId));
    }

    private ConnectedCounts connectedCounts(UUID userId, UUID personId) {
        Map<MemoryType, Long> byType = new EnumMap<>(MemoryType.class);
        for (Object[] row : people.countMemoriesByTypeForPerson(userId, personId)) {
            MemoryType type = MemoryType.valueOf(String.valueOf(row[0]));
            long n = ((Number) row[1]).longValue();
            byType.put(type, n);
        }
        long places = people.countPlacesForPerson(userId, personId);
        return new ConnectedCounts(
                byType.getOrDefault(MemoryType.CONVERSATION, 0L),
                byType.getOrDefault(MemoryType.PHOTO, 0L),
                byType.getOrDefault(MemoryType.VIDEO, 0L),
                byType.getOrDefault(MemoryType.AUDIO, 0L),
                byType.getOrDefault(MemoryType.DOCUMENT, 0L),
                byType.getOrDefault(MemoryType.TEXT, 0L),
                byType.getOrDefault(MemoryType.EVENT, 0L),
                places);
    }

    private List<Memory> hydrateOrdered(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Memory> byId = memories.findAllWithAssets(ids).stream()
                .collect(Collectors.toMap(Memory::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(m -> m != null).toList();
    }
}
