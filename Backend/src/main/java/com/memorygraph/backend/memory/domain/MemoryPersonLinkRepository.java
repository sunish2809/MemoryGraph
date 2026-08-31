package com.memorygraph.backend.memory.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryPersonLinkRepository extends JpaRepository<MemoryPersonLink, MemoryPersonLink.Pk> {

    void deleteByMemoryIdAndPersonId(UUID memoryId, UUID personId);

    boolean existsByMemoryIdAndPersonId(UUID memoryId, UUID personId);

    @Query("select mp.personId from MemoryPersonLink mp where mp.memoryId = :memoryId")
    List<UUID> findPersonIdsByMemoryId(@Param("memoryId") UUID memoryId);

    @Query("""
            select p.displayName from Person p, MemoryPersonLink mp
            where mp.personId = p.id and mp.memoryId = :memoryId
            order by p.displayName asc
            """)
    List<String> findPersonNamesByMemoryId(@Param("memoryId") UUID memoryId);

    /**
     * Photo memories that share at least one person with the given set and fall inside a calendar-day
     * window — used to attach WhatsApp (and similar) images to a conversation day in Ask.
     */
    @Query(value = """
            select m.id from memories m
            join memory_people mp on mp.memory_id = m.id
            where m.user_id = :userId
              and m.type = 'PHOTO'
              and m.occurred_at >= :fromInstant
              and m.occurred_at < :toInstant
              and mp.person_id in (:personIds)
            group by m.id, m.occurred_at
            order by m.occurred_at asc, m.id asc
            """, nativeQuery = true)
    List<UUID> findPhotoIdsSharingPeopleInWindow(
            @Param("userId") UUID userId,
            @Param("personIds") Collection<UUID> personIds,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant);

    @Query(value = """
            select m.id from memories m
            join memory_people mp on mp.memory_id = m.id
            where m.user_id = :userId and mp.person_id = :personId
            order by m.occurred_at desc, m.id desc
            """,
            countQuery = """
            select count(*) from memories m
            join memory_people mp on mp.memory_id = m.id
            where m.user_id = :userId and mp.person_id = :personId
            """,
            nativeQuery = true)
    List<UUID> findMemoryIdsByUserAndPerson(@Param("userId") UUID userId,
            @Param("personId") UUID personId, Pageable pageable);

    @Query(value = """
            select m.id from memories m
            join memory_people mp on mp.memory_id = m.id
            where m.user_id = :userId
              and mp.person_id = :personId
              and m.type = 'PHOTO'
            order by m.occurred_at desc, m.id desc
            """, nativeQuery = true)
    List<UUID> findPhotoMemoryIdsByUserAndPerson(
            @Param("userId") UUID userId, @Param("personId") UUID personId, Pageable pageable);

    @Query("select mp.memoryId from MemoryPersonLink mp where mp.personId = :personId")
    List<UUID> findMemoryIdsByPersonId(@Param("personId") UUID personId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into memory_people (memory_id, person_id)
            select mp.memory_id, :keepId
            from memory_people mp
            where mp.person_id = :sourceId
              and not exists (
                select 1 from memory_people existing
                where existing.memory_id = mp.memory_id
                  and existing.person_id = :keepId
              )
            """, nativeQuery = true)
    int copyLinksToKeep(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);

    void deleteByPersonId(UUID personId);
}
