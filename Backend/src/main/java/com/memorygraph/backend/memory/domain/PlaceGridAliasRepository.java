package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceGridAliasRepository extends JpaRepository<PlaceGridAlias, PlaceGridAlias.Pk> {

    Optional<PlaceGridAlias> findByUserIdAndNormalizedKey(UUID userId, String normalizedKey);

    List<PlaceGridAlias> findByPlaceId(UUID placeId);

    boolean existsByUserIdAndNormalizedKey(UUID userId, String normalizedKey);

    void deleteByPlaceId(UUID placeId);
}
