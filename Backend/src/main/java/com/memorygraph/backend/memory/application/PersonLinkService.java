package com.memorygraph.backend.memory.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.domain.MemoryPersonLink;
import com.memorygraph.backend.memory.domain.MemoryPersonLinkRepository;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;

import lombok.RequiredArgsConstructor;

/**
 * Upserts people by normalised display name and links them to memories.
 */
@Service
@RequiredArgsConstructor
public class PersonLinkService {

    private final PersonRepository people;
    private final MemoryPersonLinkRepository links;

    @Transactional
    public Person upsertAndLink(UUID userId, UUID memoryId, String rawSenderName, String selfDisplayName) {
        String resolved = resolveSenderName(rawSenderName, selfDisplayName);
        if (!StringUtils.hasText(resolved)) {
            return null;
        }
        return linkByDisplayName(userId, memoryId, resolved);
    }

    /** Manual or face-confirm tagging: create the person if needed, then link. */
    @Transactional
    public Person linkByDisplayName(UUID userId, UUID memoryId, String displayName) {
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        String trimmed = displayName.strip();
        String normalized = Person.normalise(trimmed);
        Person person = people.findByUserIdAndNormalizedName(userId, normalized)
                .orElseGet(() -> people.save(Person.create(userId, trimmed)));
        if (!links.existsByMemoryIdAndPersonId(memoryId, person.getId())) {
            links.save(MemoryPersonLink.of(memoryId, person.getId()));
        }
        return person;
    }

    @Transactional
    public void unlink(UUID userId, UUID memoryId, UUID personId) {
        Person person = people.findByIdAndUserId(personId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Person", personId));
        links.deleteByMemoryIdAndPersonId(memoryId, person.getId());
    }

    @Transactional(readOnly = true)
    public List<Person> listForMemory(UUID memoryId) {
        return people.findByMemoryId(memoryId);
    }

    /**
     * WhatsApp labels the exporter as {@code You}. Map that to the account display name so self is
     * one stable person across chats.
     */
    static String resolveSenderName(String rawSenderName, String selfDisplayName) {
        if (!StringUtils.hasText(rawSenderName)) {
            return null;
        }
        String trimmed = rawSenderName.strip();
        if (trimmed.equalsIgnoreCase("you") || trimmed.equalsIgnoreCase("me")) {
            return StringUtils.hasText(selfDisplayName) ? selfDisplayName.strip() : "You";
        }
        return trimmed;
    }
}
