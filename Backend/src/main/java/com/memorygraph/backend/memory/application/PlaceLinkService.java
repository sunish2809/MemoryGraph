package com.memorygraph.backend.memory.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.geocoding.ReverseGeocoder;
import com.memorygraph.backend.memory.domain.MemoryPlaceLink;
import com.memorygraph.backend.memory.domain.MemoryPlaceLinkRepository;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.memory.domain.PlaceGridAliasRepository;
import com.memorygraph.backend.memory.domain.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceLinkService {

    private final PlaceRepository places;
    private final MemoryPlaceLinkRepository links;
    private final ReverseGeocoder reverseGeocoder;
    private final PlaceGridAliasRepository aliases;

    @Transactional
    public Place upsertAndLink(UUID userId, UUID memoryId, double latitude, double longitude) {
        String key = Place.gridKey(latitude, longitude);
        Place place = resolvePlace(userId, key, latitude, longitude);
        if (place.needsGeocode()) {
            reverseGeocoder.resolveDisplayName(place.getLatitude(), place.getLongitude())
                    .ifPresent(place::applyGeocodedName);
        }
        if (!links.existsByMemoryIdAndPlaceId(memoryId, place.getId())) {
            links.save(MemoryPlaceLink.of(memoryId, place.getId()));
        }
        return place;
    }

    private Place resolvePlace(UUID userId, String key, double latitude, double longitude) {
        return places.findByUserIdAndNormalizedKey(userId, key)
                .or(() -> aliases.findByUserIdAndNormalizedKey(userId, key)
                        .flatMap(alias -> places.findByIdAndUserId(alias.getPlaceId(), userId)))
                .orElseGet(() -> places.save(Place.create(userId, latitude, longitude)));
    }
}
