package com.memorygraph.backend.memory.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.domain.PersonRepository;
import com.memorygraph.backend.memory.domain.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes people and places that no longer link to any memory (e.g. after wiping the timeline).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanEntityCleanup {

    private final PersonRepository people;
    private final PlaceRepository places;

    @Transactional
    public void pruneForUser(UUID userId) {
        int peopleRemoved = people.deleteUnlinkedForUser(userId);
        int placesRemoved = places.deleteUnlinkedForUser(userId);
        if (peopleRemoved > 0 || placesRemoved > 0) {
            log.info("Pruned {} unlinked people and {} unlinked places for user {}",
                    peopleRemoved, placesRemoved, userId);
        }
    }
}
