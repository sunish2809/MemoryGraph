package com.memorygraph.backend.memory.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaceSuggestionRejectionRepository extends JpaRepository<FaceSuggestionRejection, FaceSuggestionRejection.Pk> {

    boolean existsByFaceIdAndPersonId(UUID faceId, UUID personId);

    void deleteByFaceIdAndPersonId(UUID faceId, UUID personId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from face_suggestion_rejections r
            where r.person_id = :sourceId
              and exists (
                select 1 from face_suggestion_rejections k
                where k.face_id = r.face_id and k.person_id = :keepId
              )
            """, nativeQuery = true)
    int deleteSourceDuplicates(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update face_suggestion_rejections
            set person_id = :keepId
            where person_id = :sourceId
            """, nativeQuery = true)
    int reassignPerson(@Param("keepId") UUID keepId, @Param("sourceId") UUID sourceId);
}
