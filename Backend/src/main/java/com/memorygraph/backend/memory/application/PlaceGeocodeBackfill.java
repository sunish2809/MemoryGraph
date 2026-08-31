package com.memorygraph.backend.memory.application;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.geocoding.ReverseGeocoder;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.memory.domain.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retries reverse geocoding for places that still show coordinate labels (Nominatim miss / rate limit
 * at import time). Throttled by the geocoder itself (~1 req/s).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceGeocodeBackfill {

    private static final int BATCH = 8;

    private final PlaceRepository places;
    private final ReverseGeocoder reverseGeocoder;

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    @Transactional
    public void backfill() {
        List<Place> pending = places.findNeedingGeocode(PageRequest.of(0, BATCH));
        if (pending.isEmpty()) {
            return;
        }
        int renamed = 0;
        for (Place place : pending) {
            var name = reverseGeocoder.resolveDisplayName(place.getLatitude(), place.getLongitude());
            if (name.isPresent()) {
                place.applyGeocodedName(name.get());
                renamed++;
            }
        }
        if (renamed > 0) {
            log.info("Place geocode backfill renamed {} of {} pending place(s)", renamed, pending.size());
        }
    }
}
