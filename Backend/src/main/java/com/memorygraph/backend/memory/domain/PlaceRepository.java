package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    Optional<Place> findByUserIdAndNormalizedKey(UUID userId, String normalizedKey);

    Optional<Place> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("""
            select p from Place p
            where p.userId = :userId
            order by p.displayName asc
            """)
    List<Place> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select p from Place p, MemoryPlaceLink mp
            where mp.placeId = p.id and mp.memoryId = :memoryId
            order by p.displayName asc
            """)
    List<Place> findByMemoryId(@Param("memoryId") UUID memoryId);

    @Query("""
            select p from Place p
            where p.geocodedAt is null and p.nameLocked = false
            order by p.createdAt asc
            """)
    List<Place> findNeedingGeocode(Pageable pageable);

    @Query(value = """
            select count(*) from memory_places mp
            join memories m on m.id = mp.memory_id
            where mp.place_id = :placeId and m.user_id = :userId
            """, nativeQuery = true)
    long countMemoriesForPlace(@Param("userId") UUID userId, @Param("placeId") UUID placeId);

    /** Places with no remaining memory links (orphans after timeline wipe). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from places p
            where p.user_id = :userId
              and not exists (select 1 from memory_places mp where mp.place_id = p.id)
            """, nativeQuery = true)
    int deleteUnlinkedForUser(@Param("userId") UUID userId);
}
