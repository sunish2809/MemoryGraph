package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select t from Trip t
            where t.userId = :userId
            order by t.startedAt desc, t.id desc
            """)
    List<Trip> findAllForUser(@Param("userId") UUID userId);
}
