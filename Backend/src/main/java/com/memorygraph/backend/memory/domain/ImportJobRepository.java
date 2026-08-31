package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Optional<ImportJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<ImportJob> findByUserIdAndChecksum(UUID userId, String checksum);

    @Query("""
            select j from ImportJob j
            where j.status = com.memorygraph.backend.memory.domain.ImportJobStatus.PENDING
            order by j.createdAt asc
            """)
    List<ImportJob> findDuePending();

    @Query("""
            select j from ImportJob j
            where j.userId = :userId
            order by j.createdAt desc
            """)
    List<ImportJob> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);
}
