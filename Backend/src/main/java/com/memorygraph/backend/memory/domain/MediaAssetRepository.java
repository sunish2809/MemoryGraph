package com.memorygraph.backend.memory.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Owner-scoped like {@link MemoryRepository}: an asset is reachable only through a memory its owner
 * owns, so a leaked asset id is useless on its own.
 */
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("""
            select a from MediaAsset a
            where a.id = :assetId and a.memory.id = :memoryId and a.memory.user.id = :userId
            """)
    Optional<MediaAsset> findOwnedAsset(@Param("assetId") UUID assetId, @Param("memoryId") UUID memoryId,
            @Param("userId") UUID userId);
}
