package com.memorygraph.backend.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaceDetectionRepository extends JpaRepository<FaceDetection, UUID> {

    List<FaceDetection> findByMemoryIdOrderByCreatedAtAsc(UUID memoryId);

    Optional<FaceDetection> findByIdAndUserId(UUID id, UUID userId);

    List<FaceDetection> findByUserIdAndClusterIdAndPersonIdIsNullAndIgnoredFalse(UUID userId, UUID clusterId);

    List<FaceDetection> findByUserIdAndPersonIdIsNullAndIgnoredFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndPersonIdIsNullAndIgnoredFalse(UUID userId);

    long countByUserIdAndPersonIdIsNullAndIgnoredFalseAndSuggestedPersonIdIsNotNull(UUID userId);

    long countByMemoryIdAndPersonId(UUID memoryId, UUID personId);

    void deleteByMemoryId(UUID memoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update face_detections
            set person_id = :keepId, updated_at = now()
            where person_id = :sourceId
            """, nativeQuery = true)
    int reassignNamedPerson(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update face_detections
            set suggested_person_id = :keepId, updated_at = now()
            where suggested_person_id = :sourceId
            """, nativeQuery = true)
    int reassignSuggestedPerson(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update face_detections
            set embedding = cast(:vector as vector), updated_at = now()
            where id = :id
            """, nativeQuery = true)
    void writeEmbedding(@Param("id") UUID id, @Param("vector") String vectorLiteral);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FaceDetection f set f.clusterId = :clusterId where f.id = :id")
    int writeCluster(@Param("id") UUID id, @Param("clusterId") UUID clusterId);

    /**
     * Nearest named face, skipping people this face already rejected.
     */
    @Query(value = """
            select person_id, embedding <=> cast(:vector as vector) as distance
            from face_detections
            where user_id = :userId
              and person_id is not null
              and embedding is not null
              and person_id not in (
                  select person_id from face_suggestion_rejections where face_id = :faceId
              )
            order by embedding <=> cast(:vector as vector)
            limit 1
            """, nativeQuery = true)
    List<Object[]> findNearestNamedPerson(
            @Param("userId") UUID userId,
            @Param("faceId") UUID faceId,
            @Param("vector") String vectorLiteral);

    /**
     * Nearest unlabeled face — used to attach a new detection to an unnamed cluster.
     */
    @Query(value = """
            select id, cluster_id, embedding <=> cast(:vector as vector) as distance
            from face_detections
            where user_id = :userId
              and id <> :faceId
              and person_id is null
              and ignored = false
              and embedding is not null
            order by embedding <=> cast(:vector as vector)
            limit 1
            """, nativeQuery = true)
    List<Object[]> findNearestUnlabeled(
            @Param("userId") UUID userId,
            @Param("faceId") UUID faceId,
            @Param("vector") String vectorLiteral);

    @Query(value = "select cast(embedding as text) from face_detections where id = :id", nativeQuery = true)
    String readEmbeddingLiteral(@Param("id") UUID id);

    /**
     * Match unlabeled faces against <em>every</em> named exemplar of this person, not only the face
     * that was just confirmed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update face_detections u
            set suggested_person_id = :personId,
                confidence = 1.0 - sub.min_dist,
                updated_at = now()
            from (
                select u2.id as id, min(u2.embedding <=> n.embedding) as min_dist
                from face_detections u2
                join face_detections n
                  on n.user_id = u2.user_id
                 and n.person_id = :personId
                 and n.embedding is not null
                where u2.user_id = :userId
                  and u2.person_id is null
                  and u2.ignored = false
                  and u2.embedding is not null
                  and not exists (
                      select 1 from face_suggestion_rejections r
                      where r.face_id = u2.id and r.person_id = :personId
                  )
                group by u2.id
                having min(u2.embedding <=> n.embedding) <= :threshold
            ) sub
            where u.id = sub.id
            """, nativeQuery = true)
    int suggestUnlabeledNearAllExemplars(
            @Param("userId") UUID userId,
            @Param("personId") UUID personId,
            @Param("threshold") double threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update face_detections
            set suggested_person_id = :personId,
                confidence = 0.85,
                updated_at = now()
            where user_id = :userId
              and cluster_id = :clusterId
              and person_id is null
              and ignored = false
              and id <> :faceId
              and not exists (
                  select 1 from face_suggestion_rejections r
                  where r.face_id = face_detections.id and r.person_id = :personId
              )
            """, nativeQuery = true)
    int suggestRestOfCluster(
            @Param("userId") UUID userId,
            @Param("faceId") UUID faceId,
            @Param("clusterId") UUID clusterId,
            @Param("personId") UUID personId);
}
