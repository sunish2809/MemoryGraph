package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

    Optional<Person> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query("""
            select p from Person p
            where p.userId = :userId
            order by p.displayName asc
            """)
    java.util.List<Person> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select p from Person p, MemoryPersonLink mp
            where mp.personId = p.id and mp.memoryId = :memoryId
            order by p.displayName asc
            """)
    List<Person> findByMemoryId(@Param("memoryId") UUID memoryId);

    @Query(value = """
            select count(*) from memory_people mp
            join memories m on m.id = mp.memory_id
            where mp.person_id = :personId and m.user_id = :userId
            """, nativeQuery = true)
    long countMemoriesForPerson(@Param("userId") UUID userId, @Param("personId") UUID personId);

    @Query(value = """
            select m.type, count(*) from memories m
            join memory_people mp on mp.memory_id = m.id
            where mp.person_id = :personId and m.user_id = :userId
            group by m.type
            """, nativeQuery = true)
    List<Object[]> countMemoriesByTypeForPerson(@Param("userId") UUID userId, @Param("personId") UUID personId);

    @Query(value = """
            select count(distinct mpl.place_id) from memory_places mpl
            join memory_people mp on mp.memory_id = mpl.memory_id
            join memories m on m.id = mp.memory_id
            where mp.person_id = :personId and m.user_id = :userId
            """, nativeQuery = true)
    long countPlacesForPerson(@Param("userId") UUID userId, @Param("personId") UUID personId);

    /** People with no remaining memory links (orphans after timeline wipe). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from people p
            where p.user_id = :userId
              and not exists (select 1 from memory_people mp where mp.person_id = p.id)
            """, nativeQuery = true)
    int deleteUnlinkedForUser(@Param("userId") UUID userId);
}
