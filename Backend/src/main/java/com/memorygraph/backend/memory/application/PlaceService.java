package com.memorygraph.backend.memory.application;

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
import com.memorygraph.backend.memory.api.dto.PlaceDetailResponse;
import com.memorygraph.backend.memory.api.dto.PlaceSummaryResponse;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryPlaceLinkRepository;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.memory.domain.PlaceGridAlias;
import com.memorygraph.backend.memory.domain.PlaceGridAliasRepository;
import com.memorygraph.backend.memory.domain.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int RECENT_MEMORIES = 50;

    private final PlaceRepository places;
    private final MemoryPlaceLinkRepository links;
    private final MemoryRepository memories;
    private final PlaceGridAliasRepository aliases;

    @Transactional(readOnly = true)
    public List<PlaceSummaryResponse> list(UUID userId) {
        return places.findAllForUser(userId).stream()
                .map(place -> PlaceSummaryResponse.from(place,
                        places.countMemoriesForPlace(userId, place.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse get(UUID userId, UUID placeId) {
        Place place = places.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", placeId));
        long count = places.countMemoriesForPlace(userId, placeId);
        List<UUID> ids = links.findMemoryIdsByUserAndPlace(userId, placeId, PageRequest.of(0, RECENT_MEMORIES));
        List<MemorySummaryResponse> recent = hydrateOrdered(ids).stream().map(MemorySummaryResponse::from).toList();
        return PlaceDetailResponse.from(place, count, recent);
    }

    @Transactional
    public PlaceDetailResponse rename(UUID userId, UUID placeId, String displayName) {
        Place place = requireOwned(userId, placeId);
        place.rename(displayName);
        return get(userId, placeId);
    }

    /**
     * Moves every memory link from {@code sourcePlaceId} onto {@code keepId}, remembers the source
     * GPS cell as an alias so later photos do not recreate it, then deletes the duplicate.
     */
    @Transactional
    public PlaceDetailResponse merge(UUID userId, UUID keepId, UUID sourcePlaceId) {
        if (keepId.equals(sourcePlaceId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cannot merge a place into itself");
        }
        Place keep = requireOwned(userId, keepId);
        Place source = requireOwned(userId, sourcePlaceId);
        links.copyLinksToKeep(keepId, sourcePlaceId);
        links.deleteByPlaceId(sourcePlaceId);
        for (PlaceGridAlias alias : aliases.findByPlaceId(source.getId())) {
            if (!aliases.existsByUserIdAndNormalizedKey(userId, alias.getNormalizedKey())
                    && !alias.getNormalizedKey().equals(keep.getNormalizedKey())) {
                alias.reassign(keepId);
            } else {
                aliases.delete(alias);
            }
        }
        if (!source.getNormalizedKey().equals(keep.getNormalizedKey())
                && !aliases.existsByUserIdAndNormalizedKey(userId, source.getNormalizedKey())) {
            aliases.save(PlaceGridAlias.of(userId, source.getNormalizedKey(), keepId));
        }
        places.delete(source);
        places.flush();
        return get(userId, keepId);
    }

    private Place requireOwned(UUID userId, UUID placeId) {
        return places.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Place", placeId));
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
