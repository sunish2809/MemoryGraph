package com.memorygraph.backend.memory.application.face;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.ai.embedding.MemoryEmbeddingStore;
import com.memorygraph.backend.common.api.ApiPaths;
import com.memorygraph.backend.common.config.FacesProperties;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.api.dto.FaceDetectionResponse;
import com.memorygraph.backend.memory.api.dto.FaceReviewResponse;
import com.memorygraph.backend.memory.api.dto.FaceReviewResponse.FaceClusterGroup;
import com.memorygraph.backend.memory.api.dto.FaceReviewResponse.FaceReviewItem;
import com.memorygraph.backend.memory.application.PersonLinkService;
import com.memorygraph.backend.memory.application.upload.DisplayableImage;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.SupportedMediaType;
import com.memorygraph.backend.memory.domain.FaceDetection;
import com.memorygraph.backend.memory.domain.FaceDetectionRepository;
import com.memorygraph.backend.memory.domain.FaceSuggestionRejection;
import com.memorygraph.backend.memory.domain.FaceSuggestionRejectionRepository;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;
import com.memorygraph.backend.storage.StorageKey;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceService {

    private static final int REVIEW_LIMIT = 200;
    private static final int CLUSTER_PREVIEW = 12;
    static final String VIDEO_FRAME_PREFIX = "face-frame-";

    private final FaceDetectionRepository faces;
    private final FaceSuggestionRejectionRepository rejections;
    private final FaceDetectionClient detectionClient;
    private final MemoryRepository memories;
    private final PersonRepository people;
    private final PersonLinkService personLinks;
    private final StorageService storage;
    private final FacesProperties properties;
    private final HeicToJpegConverter heicToJpeg;
    private final VideoFrameExtractor videoFrames;

    @Transactional(readOnly = true)
    public List<FaceDetectionResponse> listForMemory(UUID userId, UUID memoryId) {
        memories.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));
        List<FaceDetection> detections = faces.findByMemoryIdOrderByCreatedAtAsc(memoryId);
        Map<UUID, String> names = namesFor(detections);
        List<FaceDetectionResponse> result = new ArrayList<>(detections.size());
        for (FaceDetection detection : detections) {
            result.add(toResponse(detection, names.get(detection.getPersonId()),
                    names.get(detection.getSuggestedPersonId()), null));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public FaceReviewResponse review(UUID userId) {
        long unlabeled = faces.countByUserIdAndPersonIdIsNullAndIgnoredFalse(userId);
        long suggested = faces.countByUserIdAndPersonIdIsNullAndIgnoredFalseAndSuggestedPersonIdIsNotNull(userId);
        List<FaceDetection> detections = faces.findByUserIdAndPersonIdIsNullAndIgnoredFalseOrderByCreatedAtDesc(
                userId, PageRequest.of(0, REVIEW_LIMIT));
        if (detections.isEmpty()) {
            return new FaceReviewResponse(unlabeled, suggested, List.of());
        }
        Map<UUID, Memory> byMemory = memories.findAllWithAssets(
                detections.stream().map(FaceDetection::getMemoryId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Memory::getId, memory -> memory));
        java.util.Set<UUID> webpAssets = byMemory.values().stream()
                .flatMap(memory -> memory.getAssets().stream())
                .filter(asset -> SupportedMediaType.isWebp(asset.getFileName(), asset.getMimeType()))
                .map(MediaAsset::getId)
                .collect(Collectors.toSet());
        int before = detections.size();
        detections = detections.stream()
                .filter(detection -> !webpAssets.contains(detection.getAssetId()))
                .toList();
        unlabeled = Math.max(0, unlabeled - (before - detections.size()));
        suggested = detections.stream().filter(detection -> detection.getSuggestedPersonId() != null).count();
        if (detections.isEmpty()) {
            return new FaceReviewResponse(unlabeled, 0, List.of());
        }
        Map<UUID, String> names = namesFor(detections);

        Map<UUID, List<FaceDetection>> grouped = new LinkedHashMap<>();
        List<FaceDetection> singles = new ArrayList<>();
        for (FaceDetection detection : detections) {
            if (detection.getClusterId() != null) {
                grouped.computeIfAbsent(detection.getClusterId(), key -> new ArrayList<>()).add(detection);
            } else {
                singles.add(detection);
            }
        }

        List<FaceClusterGroup> groups = new ArrayList<>();
        grouped.values().stream()
                .sorted(Comparator.comparingInt((List<FaceDetection> members) -> members.size()).reversed())
                .forEach(members -> groups.add(toGroup(members, names, byMemory)));
        for (FaceDetection single : singles) {
            groups.add(toGroup(List.of(single), names, byMemory));
        }
        return new FaceReviewResponse(unlabeled, suggested, groups);
    }

    @Transactional
    public void detectForMemory(Memory memory) {
        if (!detectionClient.isAvailable()) {
            return;
        }
        if (memory.getType() != MemoryType.PHOTO && memory.getType() != MemoryType.VIDEO) {
            return;
        }
        if (memory.getType() == MemoryType.VIDEO) {
            extractVideoFrames(memory);
        }
        faces.deleteByMemoryId(memory.getId());
        for (MediaAsset asset : memory.getAssets()) {
            if (asset.getMimeType() == null || !asset.getMimeType().startsWith("image/")) {
                continue;
            }
            if (SupportedMediaType.isWebp(asset.getFileName(), asset.getMimeType())) {
                continue;
            }
            byte[] bytes = readBytes(asset);
            if (bytes.length == 0) {
                continue;
            }
            if (heicToJpeg.looksLikeHeic(asset.getMimeType())) {
                var displayable = heicToJpeg.toDisplayable(bytes, asset.getFileName(), SupportedMediaType.HEIC);
                bytes = displayable.bytes();
                if (displayable.mediaType() == SupportedMediaType.JPEG) {
                    persistConverted(asset, displayable);
                }
            }
            List<DetectedFace> detected = detectionClient.detect(bytes, asset.getMimeType());
            for (DetectedFace face : detected) {
                FaceDetection row = FaceDetection.create(
                        memory.getId(),
                        memory.getUserId(),
                        asset.getId(),
                        face.x(),
                        face.y(),
                        face.width(),
                        face.height());
                FaceDetection saved = faces.save(row);
                if (face.embedding() != null && face.embedding().length > 0) {
                    String literal = MemoryEmbeddingStore.toVectorLiteral(face.embedding());
                    faces.writeEmbedding(saved.getId(), literal);
                    suggestMatch(memory.getUserId(), saved, literal);
                    assignCluster(memory.getUserId(), saved, literal);
                }
            }
            log.info("Detected {} face(s) on memory {} asset {}", detected.size(), memory.getId(), asset.getId());
        }
    }

    @Transactional
    public FaceDetectionResponse confirm(UUID userId, UUID faceId, UUID personId, String displayName) {
        FaceDetection face = faces.findByIdAndUserId(faceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face", faceId));
        Person person = resolvePerson(userId, face.getMemoryId(), personId, displayName);
        rejections.deleteByFaceIdAndPersonId(face.getId(), person.getId());
        face.assignPerson(person.getId());
        faces.flush();
        int alsoSuggested = faces.suggestUnlabeledNearAllExemplars(
                userId, person.getId(), properties.matchThreshold());
        if (face.getClusterId() != null) {
            alsoSuggested += faces.suggestRestOfCluster(userId, face.getId(), face.getClusterId(), person.getId());
        }
        if (alsoSuggested > 0) {
            log.info("Tagged face {} as {}; suggested the same person on {} other face(s)",
                    face.getId(), person.getDisplayName(), alsoSuggested);
        }
        return toResponse(face, person.getDisplayName(), person.getDisplayName(), alsoSuggested);
    }

    @Transactional
    public FaceReviewResponse confirmCluster(UUID userId, UUID clusterId, UUID personId, String displayName) {
        List<FaceDetection> members = faces.findByUserIdAndClusterIdAndPersonIdIsNullAndIgnoredFalse(userId, clusterId);
        if (members.isEmpty()) {
            throw new ResourceNotFoundException("Face cluster", clusterId);
        }
        FaceDetection first = members.get(0);
        Person person = resolvePerson(userId, first.getMemoryId(), personId, displayName);
        for (FaceDetection member : members) {
            rejections.deleteByFaceIdAndPersonId(member.getId(), person.getId());
            personLinks.linkByDisplayName(userId, member.getMemoryId(), person.getDisplayName());
            member.assignPerson(person.getId());
        }
        faces.flush();
        faces.suggestUnlabeledNearAllExemplars(userId, person.getId(), properties.matchThreshold());
        return review(userId);
    }

    @Transactional
    public FaceDetectionResponse ignore(UUID userId, UUID faceId) {
        FaceDetection face = faces.findByIdAndUserId(faceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face", faceId));
        if (face.getPersonId() != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Clear the name before skipping this face");
        }
        face.ignore();
        return toResponse(face, null, null, 0);
    }

    @Transactional
    public FaceDetectionResponse restore(UUID userId, UUID faceId) {
        FaceDetection face = faces.findByIdAndUserId(faceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face", faceId));
        face.restore();
        String suggestedName = null;
        if (face.getSuggestedPersonId() != null) {
            suggestedName = people.findById(face.getSuggestedPersonId())
                    .map(Person::getDisplayName)
                    .orElse(null);
        }
        return toResponse(face, null, suggestedName, 0);
    }

    @Transactional
    public FaceReviewResponse ignoreCluster(UUID userId, UUID clusterId) {
        List<FaceDetection> members = faces.findByUserIdAndClusterIdAndPersonIdIsNullAndIgnoredFalse(userId, clusterId);
        if (members.isEmpty()) {
            throw new ResourceNotFoundException("Face cluster", clusterId);
        }
        for (FaceDetection member : members) {
            member.ignore();
        }
        faces.flush();
        return review(userId);
    }

    @Transactional
    public FaceDetectionResponse rejectSuggestion(UUID userId, UUID faceId) {
        FaceDetection face = faces.findByIdAndUserId(faceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face", faceId));
        UUID suggested = face.getSuggestedPersonId();
        if (suggested == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This face has no suggestion to reject");
        }
        if (!rejections.existsByFaceIdAndPersonId(faceId, suggested)) {
            rejections.save(FaceSuggestionRejection.of(faceId, suggested));
        }
        face.clearSuggestion();
        return toResponse(face, null, null, 0);
    }

    @Transactional
    public FaceDetectionResponse clearAssignment(UUID userId, UUID memoryId, UUID faceId) {
        FaceDetection face = faces.findByIdAndUserId(faceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Face", faceId));
        if (!face.getMemoryId().equals(memoryId)) {
            throw new ResourceNotFoundException("Face", faceId);
        }
        UUID was = face.getPersonId();
        face.clearPerson();
        faces.flush();
        if (was != null && faces.countByMemoryIdAndPersonId(memoryId, was) == 0) {
            personLinks.unlink(userId, memoryId, was);
        }
        String suggestedName = null;
        if (face.getSuggestedPersonId() != null) {
            suggestedName = people.findById(face.getSuggestedPersonId())
                    .map(Person::getDisplayName)
                    .orElse(null);
        }
        return toResponse(face, null, suggestedName, 0);
    }

    private Person resolvePerson(UUID userId, UUID memoryId, UUID personId, String displayName) {
        if (personId != null) {
            Person person = people.findByIdAndUserId(personId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Person", personId));
            personLinks.linkByDisplayName(userId, memoryId, person.getDisplayName());
            return person;
        }
        return personLinks.linkByDisplayName(userId, memoryId, displayName);
    }

    private void extractVideoFrames(Memory memory) {
        boolean alreadyExtracted = memory.getAssets().stream()
                .anyMatch(asset -> asset.getFileName() != null
                        && asset.getFileName().startsWith(VIDEO_FRAME_PREFIX));
        if (alreadyExtracted) {
            return;
        }
        List<MediaAsset> videos = memory.getAssets().stream()
                .filter(asset -> asset.getMimeType() != null && asset.getMimeType().startsWith("video/"))
                .toList();
        int stored = 0;
        for (MediaAsset video : videos) {
            byte[] bytes = readBytes(video);
            if (bytes.length == 0) {
                continue;
            }
            List<byte[]> frames = videoFrames.extract(
                    bytes, video.getMimeType(), properties.videoMaxFrames(), properties.videoFrameIntervalSeconds());
            for (byte[] jpeg : frames) {
                stored++;
                String fileName = "%s%03d.jpg".formatted(VIDEO_FRAME_PREFIX, stored);
                StorageKey key = StorageKey.forMemoryAsset(memory.getUserId(), memory.getId(), fileName);
                StoredObject storedObject = storage.store(key, new ByteArrayInputStream(jpeg), jpeg.length);
                memory.attach(MediaAsset.of(
                        key, fileName, "image/jpeg", storedObject.sizeBytes(), storedObject.checksum()));
            }
        }
        if (stored > 0) {
            memories.flush();
            log.info("Extracted {} still(s) from video memory {}", stored, memory.getId());
        }
    }

    private void suggestMatch(UUID userId, FaceDetection saved, String vectorLiteral) {
        List<Object[]> nearest = faces.findNearestNamedPerson(userId, saved.getId(), vectorLiteral);
        if (nearest.isEmpty()) {
            return;
        }
        Object[] row = nearest.get(0);
        UUID personId = (UUID) row[0];
        double distance = ((Number) row[1]).doubleValue();
        if (distance <= properties.matchThreshold()) {
            saved.suggest(personId, 1.0 - distance);
        }
    }

    private void assignCluster(UUID userId, FaceDetection saved, String vectorLiteral) {
        List<Object[]> nearest = faces.findNearestUnlabeled(userId, saved.getId(), vectorLiteral);
        if (nearest.isEmpty()) {
            return;
        }
        Object[] row = nearest.get(0);
        UUID otherId = (UUID) row[0];
        UUID otherCluster = (UUID) row[1];
        double distance = ((Number) row[2]).doubleValue();
        if (distance > properties.clusterThreshold()) {
            return;
        }
        UUID clusterId = otherCluster != null ? otherCluster : UUID.randomUUID();
        if (otherCluster == null) {
            faces.writeCluster(otherId, clusterId);
        }
        saved.assignCluster(clusterId);
    }

    private FaceClusterGroup toGroup(
            List<FaceDetection> members, Map<UUID, String> names, Map<UUID, Memory> byMemory) {
        UUID clusterId = members.size() > 1 ? members.get(0).getClusterId() : members.get(0).getClusterId();
        if (members.size() == 1 && members.get(0).getClusterId() == null) {
            clusterId = null;
        }
        UUID suggestedId = members.stream()
                .map(FaceDetection::getSuggestedPersonId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
        List<FaceReviewItem> preview = members.stream()
                .limit(CLUSTER_PREVIEW)
                .map(face -> toReviewItem(face, names, byMemory.get(face.getMemoryId())))
                .toList();
        return new FaceClusterGroup(
                clusterId,
                members.size(),
                suggestedId,
                names.get(suggestedId),
                preview);
    }

    private FaceReviewItem toReviewItem(FaceDetection face, Map<UUID, String> names, Memory memory) {
        String title = memory != null ? memory.getTitle() : null;
        return new FaceReviewItem(
                face.getId(),
                face.getMemoryId(),
                title,
                memory != null ? memory.getType() : null,
                memory != null ? memory.getOccurredAt() : null,
                face.getAssetId(),
                "%s/memories/%s/media/%s".formatted(ApiPaths.V1, face.getMemoryId(), face.getAssetId()),
                face.getX(),
                face.getY(),
                face.getWidth(),
                face.getHeight(),
                face.getSuggestedPersonId(),
                names.get(face.getSuggestedPersonId()),
                face.getConfidence(),
                face.getClusterId());
    }

    private Map<UUID, String> namesFor(List<FaceDetection> detections) {
        Map<UUID, String> names = new HashMap<>();
        for (FaceDetection detection : detections) {
            collectName(names, detection.getPersonId());
            collectName(names, detection.getSuggestedPersonId());
        }
        return names;
    }

    private void collectName(Map<UUID, String> names, UUID personId) {
        if (personId == null || names.containsKey(personId)) {
            return;
        }
        people.findById(personId).ifPresent(p -> names.put(personId, p.getDisplayName()));
    }

    private FaceDetectionResponse toResponse(
            FaceDetection face, String personName, String suggestedName, Integer alsoSuggested) {
        return new FaceDetectionResponse(
                face.getId(),
                face.getMemoryId(),
                face.getAssetId(),
                face.getX(),
                face.getY(),
                face.getWidth(),
                face.getHeight(),
                face.getPersonId(),
                personName,
                face.getSuggestedPersonId(),
                suggestedName,
                face.getConfidence(),
                face.getClusterId(),
                alsoSuggested,
                face.isIgnored());
    }

    private void persistConverted(MediaAsset asset, DisplayableImage displayable) {
        try {
            var stored = storage.store(asset.key(),
                    new ByteArrayInputStream(displayable.bytes()), displayable.bytes().length);
            asset.replacePayload(displayable.fileName(), displayable.mediaType().mimeType(), stored.sizeBytes(),
                    stored.checksum());
        } catch (RuntimeException ex) {
            log.warn("Converted HEIC for face detect but could not persist JPEG for asset {}: {}",
                    asset.getId(), ex.getMessage());
        }
    }

    private byte[] readBytes(MediaAsset asset) {
        try {
            Resource resource = storage.retrieve(asset.key());
            return resource.getInputStream().readAllBytes();
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read asset {} for face detection: {}", asset.getId(), ex.getMessage());
            return new byte[0];
        }
    }
}
