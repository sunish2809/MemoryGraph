package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Owner-scoped by design: every finder takes the owning user id, so there is no access path that
 * could leak one user's memories to another. Queries are written out rather than derived so the
 * traversal to the owner is unambiguous.
 */
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    @Query("select m from Memory m left join fetch m.assets where m.id = :id and m.user.id = :userId")
    Optional<Memory> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Newest first: a memory list is almost always read from the present backwards.
     * <p>
     * Returns ids rather than entities, because a page of memories has to arrive with its media
     * already loaded — and joining a collection would force the database to hand over every row so
     * pagination could be redone in memory. Pairing this with {@link #findAllWithAssets} keeps
     * pagination in the database and still loads the whole graph in one further query.
     */
    @Query("select m.id from Memory m where m.user.id = :userId order by m.occurredAt desc, m.id desc")
    Page<UUID> findTimelineIds(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The same ordering, restricted to a window. The bounds are inclusive of {@code from} and
     * exclusive of {@code to}, so consecutive windows neither overlap nor drop a memory.
     */
    @Query("""
            select m.id from Memory m
            where m.user.id = :userId and m.occurredAt >= :from and m.occurredAt < :to
            order by m.occurredAt desc, m.id desc
            """)
    Page<UUID> findTimelineWindowIds(@Param("userId") UUID userId, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /** Loads memories with their media in one query, preserving the timeline ordering. */
    @Query("""
            select distinct m from Memory m
            left join fetch m.assets
            where m.id in :ids
            order by m.occurredAt desc, m.id desc
            """)
    List<Memory> findAllWithAssets(@Param("ids") Collection<UUID> ids);

    @Query("select m.id from Memory m where m.user.id = :userId order by m.occurredAt desc, m.id desc")
    List<UUID> findAllIdsByUserId(@Param("userId") UUID userId);

    @Query("select count(m) from Memory m where m.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    @Query("select min(m.occurredAt) from Memory m where m.user.id = :userId")
    Optional<Instant> findEarliestOccurrence(@Param("userId") UUID userId);

    @Query("select m.id from Memory m where m.user.id = :userId and m.importJobId = :importJobId")
    List<UUID> findIdsByUserIdAndImportJobId(@Param("userId") UUID userId, @Param("importJobId") UUID importJobId);

    @Query("""
            select m.id from Memory m
            where m.user.id = :userId
              and m.source = com.memorygraph.backend.memory.domain.MemorySource.IMPORT
              and (
                m.title like concat('WhatsApp · ', :chatName, ' · %')
                or m.description = concat('From WhatsApp · ', :chatName)
              )
            """)
    List<UUID> findLegacyWhatsAppImportIds(
            @Param("userId") UUID userId, @Param("chatName") String chatName);

    @Query("""
            select m.id from Memory m
            where m.user.id = :userId and m.occurredAt >= :from and m.occurredAt < :to
            """)
    List<UUID> findIdsInOccurredWindow(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select m.id from Memory m
            where m.user.id = :userId and m.occurredAt >= :from and m.occurredAt <= :to
            order by m.occurredAt asc, m.id asc
            """)
    List<UUID> findIdsInInclusiveOccurredWindow(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select m.id from Memory m
            where m.user.id = :userId
              and exists (select 1 from MemoryPlaceLink mp where mp.memoryId = m.id)
            order by m.occurredAt asc, m.id asc
            """)
    List<UUID> findIdsWithPlacesOrdered(@Param("userId") UUID userId);

    /**
     * WhatsApp photo memories for a chat on a given day ({@code description} is
     * {@code From WhatsApp · {chatName}}).
     */
    @Query("""
            select m.id from Memory m
            where m.user.id = :userId
              and m.type = :type
              and m.description = :description
              and m.occurredAt >= :fromInstant
              and m.occurredAt < :toInstant
            order by m.occurredAt asc, m.id asc
            """)
    List<UUID> findWhatsAppPhotoIdsForChatDay(
            @Param("userId") UUID userId,
            @Param("type") MemoryType type,
            @Param("description") String description,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant);
}
