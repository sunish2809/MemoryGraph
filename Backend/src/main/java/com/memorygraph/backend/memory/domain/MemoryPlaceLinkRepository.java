package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryPlaceLinkRepository extends JpaRepository<MemoryPlaceLink, MemoryPlaceLink.Pk> {

    boolean existsByMemoryIdAndPlaceId(UUID memoryId, UUID placeId);

    @Query(value = """
            select m.id from memories m
            join memory_places mp on mp.memory_id = m.id
            where m.user_id = :userId and mp.place_id = :placeId
            order by m.occurred_at desc, m.id desc
            """,
            countQuery = """
            select count(*) from memories m
            join memory_places mp on mp.memory_id = m.id
            where m.user_id = :userId and mp.place_id = :placeId
            """,
            nativeQuery = true)
    List<UUID> findMemoryIdsByUserAndPlace(@Param("userId") UUID userId, @Param("placeId") UUID placeId,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into memory_places (memory_id, place_id)
            select mp.memory_id, :keepId
            from memory_places mp
            where mp.place_id = :sourceId
              and not exists (
                select 1 from memory_places existing
                where existing.memory_id = mp.memory_id
                  and existing.place_id = :keepId
              )
            """, nativeQuery = true)
    int copyLinksToKeep(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);

    void deleteByPlaceId(UUID placeId);
}
