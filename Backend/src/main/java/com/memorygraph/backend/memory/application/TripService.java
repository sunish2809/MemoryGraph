package com.memorygraph.backend.memory.application;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.api.dto.CreateTripRequest;
import com.memorygraph.backend.memory.api.dto.MemorySummaryResponse;
import com.memorygraph.backend.memory.api.dto.PersonSummaryResponse;
import com.memorygraph.backend.memory.api.dto.PlaceSummaryResponse;
import com.memorygraph.backend.memory.api.dto.TripDetailResponse;
import com.memorygraph.backend.memory.api.dto.TripSuggestionResponse;
import com.memorygraph.backend.memory.api.dto.TripSummaryResponse;
import com.memorygraph.backend.memory.api.dto.TripsPageResponse;
import com.memorygraph.backend.memory.api.dto.UpdateTripRequest;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.memory.domain.PlaceRepository;
import com.memorygraph.backend.memory.domain.Trip;
import com.memorygraph.backend.memory.domain.TripRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripService {

    private static final Duration GAP = Duration.ofHours(36);
    private static final int MEMORY_PREVIEW = 80;

    private final TripRepository trips;
    private final MemoryRepository memories;
    private final PlaceRepository places;
    private final PersonRepository people;

    @Transactional(readOnly = true)
    public TripsPageResponse list(UUID userId) {
        List<Trip> saved = trips.findAllForUser(userId);
        List<TripSummaryResponse> summaries = saved.stream().map(trip -> toSummary(userId, trip)).toList();
        return new TripsPageResponse(summaries, suggestions(userId, saved));
    }

    @Transactional(readOnly = true)
    public TripDetailResponse get(UUID userId, UUID tripId) {
        Trip trip = requireOwned(userId, tripId);
        Window window = window(userId, trip.getStartedAt(), trip.getEndedAt());
        List<MemorySummaryResponse> preview = window.memories().stream()
                .limit(MEMORY_PREVIEW)
                .map(MemorySummaryResponse::from)
                .toList();
        return TripDetailResponse.from(
                trip, window.memories().size(), window.placeSummaries(), window.personSummaries(), preview);
    }

    @Transactional
    public TripDetailResponse create(UUID userId, CreateTripRequest request) {
        requireRange(request.startedAt(), request.endedAt());
        Trip trip = trips.save(Trip.create(
                userId, request.title(), request.startedAt(), request.endedAt(), request.notes()));
        return get(userId, trip.getId());
    }

    @Transactional
    public TripDetailResponse update(UUID userId, UUID tripId, UpdateTripRequest request) {
        Trip trip = requireOwned(userId, tripId);
        Instant started = request.startedAt() != null ? request.startedAt() : trip.getStartedAt();
        Instant ended = request.endedAt() != null ? request.endedAt() : trip.getEndedAt();
        requireRange(started, ended);
        trip.edit(request.title(), request.startedAt(), request.endedAt(), request.notes());
        return get(userId, tripId);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId) {
        trips.delete(requireOwned(userId, tripId));
    }

    private List<TripSuggestionResponse> suggestions(UUID userId, List<Trip> saved) {
        List<UUID> ids = memories.findIdsWithPlacesOrdered(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Memory> placed = hydrateChronological(ids);
        UUID homeId = homePlaceId(userId);
        List<TripSuggestionResponse> result = new ArrayList<>();
        List<Memory> cluster = new ArrayList<>();
        for (Memory memory : placed) {
            if (memory.getOccurredAt() == null) {
                continue;
            }
            if (!cluster.isEmpty()) {
                Instant previous = cluster.get(cluster.size() - 1).getOccurredAt();
                if (Duration.between(previous, memory.getOccurredAt()).compareTo(GAP) > 0) {
                    maybeSuggest(userId, cluster, homeId, saved, result);
                    cluster = new ArrayList<>();
                }
            }
            cluster.add(memory);
        }
        maybeSuggest(userId, cluster, homeId, saved, result);
        return result;
    }

    private void maybeSuggest(
            UUID userId,
            List<Memory> cluster,
            UUID homeId,
            List<Trip> saved,
            List<TripSuggestionResponse> into) {
        if (cluster.size() < 2) {
            return;
        }
        Instant started = cluster.get(0).getOccurredAt();
        Instant ended = cluster.get(cluster.size() - 1).getOccurredAt();
        Window window = window(userId, started, ended);
        if (window.places().size() == 1 && homeId != null && window.places().get(0).getId().equals(homeId)) {
            return;
        }
        if (overlapsSaved(started, ended, saved)) {
            return;
        }
        into.add(new TripSuggestionResponse(
                suggestTitle(window, started),
                started,
                ended,
                window.memories().size(),
                window.places().size(),
                window.people().size(),
                window.primaryPlaceName(),
                window.placeSummaries(),
                window.personSummaries()));
    }

    private boolean overlapsSaved(Instant started, Instant ended, List<Trip> saved) {
        Duration span = Duration.between(started, ended);
        for (Trip trip : saved) {
            Instant overlapStart = started.isAfter(trip.getStartedAt()) ? started : trip.getStartedAt();
            Instant overlapEnd = ended.isBefore(trip.getEndedAt()) ? ended : trip.getEndedAt();
            if (overlapEnd.isBefore(overlapStart)) {
                continue;
            }
            if (span.isZero() || span.isNegative()) {
                return true;
            }
            Duration overlap = Duration.between(overlapStart, overlapEnd);
            if (overlap.toMillis() * 2 >= span.toMillis()) {
                return true;
            }
        }
        return false;
    }

    private UUID homePlaceId(UUID userId) {
        return places.findAllForUser(userId).stream()
                .max(Comparator.comparingLong(place -> places.countMemoriesForPlace(userId, place.getId())))
                .map(Place::getId)
                .orElse(null);
    }

    private String suggestTitle(Window window, Instant started) {
        int year = started.atZone(ZoneOffset.UTC).getYear();
        List<Place> named = window.places();
        if (named.isEmpty()) {
            return "Trip, " + year;
        }
        if (named.size() == 1) {
            return named.get(0).getDisplayName() + ", " + year;
        }
        if (named.size() == 2) {
            return named.get(0).getDisplayName() + " & " + named.get(1).getDisplayName() + ", " + year;
        }
        String primary = window.primaryPlaceName() != null ? window.primaryPlaceName() : named.get(0).getDisplayName();
        return primary + " trip, " + year;
    }

    private TripSummaryResponse toSummary(UUID userId, Trip trip) {
        Window window = window(userId, trip.getStartedAt(), trip.getEndedAt());
        return TripSummaryResponse.from(
                trip,
                window.memories().size(),
                window.places().size(),
                window.people().size(),
                window.primaryPlaceName());
    }

    private Window window(UUID userId, Instant started, Instant ended) {
        List<UUID> ids = memories.findIdsInInclusiveOccurredWindow(userId, started, ended);
        List<Memory> rows = hydrateChronological(ids);
        Map<UUID, Place> byPlace = new LinkedHashMap<>();
        Map<UUID, Long> placeHits = new LinkedHashMap<>();
        Map<UUID, Person> byPerson = new LinkedHashMap<>();
        for (Memory memory : rows) {
            for (Place place : places.findByMemoryId(memory.getId())) {
                byPlace.putIfAbsent(place.getId(), place);
                placeHits.merge(place.getId(), 1L, Long::sum);
            }
            for (Person person : people.findByMemoryId(memory.getId())) {
                byPerson.putIfAbsent(person.getId(), person);
            }
        }
        List<Place> placeList = new ArrayList<>(byPlace.values());
        UUID primaryId = placeHits.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        String primaryName = primaryId != null && byPlace.containsKey(primaryId)
                ? byPlace.get(primaryId).getDisplayName()
                : (placeList.isEmpty() ? null : placeList.get(0).getDisplayName());
        List<PlaceSummaryResponse> placeSummaries = placeList.stream()
                .map(place -> PlaceSummaryResponse.from(place, places.countMemoriesForPlace(userId, place.getId())))
                .toList();
        List<PersonSummaryResponse> personSummaries = byPerson.values().stream()
                .map(person -> PersonSummaryResponse.from(person, people.countMemoriesForPerson(userId, person.getId())))
                .toList();
        return new Window(rows, placeList, new ArrayList<>(byPerson.values()), primaryName, placeSummaries,
                personSummaries);
    }

    private List<Memory> hydrateChronological(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Memory> byId = memories.findAllWithAssets(ids).stream()
                .collect(Collectors.toMap(Memory::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(memory -> memory != null).toList();
    }

    private Trip requireOwned(UUID userId, UUID tripId) {
        return trips.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
    }

    private static void requireRange(Instant started, Instant ended) {
        if (ended.isBefore(started)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A trip cannot end before it starts");
        }
    }

    private record Window(
            List<Memory> memories,
            List<Place> places,
            List<Person> people,
            String primaryPlaceName,
            List<PlaceSummaryResponse> placeSummaries,
            List<PersonSummaryResponse> personSummaries) {
    }
}
