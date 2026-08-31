package com.memorygraph.backend.memory.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.api.dto.MemoryStatsResponse;
import com.memorygraph.backend.memory.application.processing.EmbeddingJobEnqueuer;
import com.memorygraph.backend.memory.application.processing.ProcessingJobQueued;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.UploadValidator;
import com.memorygraph.backend.memory.application.upload.ValidatedUpload;
import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.ConversationMessageRepository;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;
import com.memorygraph.backend.memory.domain.PlaceRepository;
import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.memory.domain.ProcessingStatus;
import com.memorygraph.backend.storage.StorageKey;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Everything the application does with memories. The only entry point the API layer uses, so the
 * rules that matter — an upload is validated before it is stored, a memory is only ever reachable by
 * its owner, deleting a memory deletes its bytes — live in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memories;
    private final ConversationMessageRepository conversationMessages;
    private final ProcessingJobRepository jobs;
    private final PersonRepository people;
    private final PlaceRepository places;
    private final UserRepository users;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final ApplicationEventPublisher events;
    private final EmbeddingJobEnqueuer embeddingJobs;
    private final PersonLinkService personLinks;
    private final OrphanEntityCleanup orphanCleanup;
    private final HeicToJpegConverter heicToJpeg;

    /**
     * A typed note is complete on arrival — its text is already the searchable content — but an
     * embedding still has to be computed asynchronously, so semantic search and Ask can find it.
     */
    @Transactional
    public Memory createTextMemory(UUID userId, String title, String description, String content,
            Instant occurredAt) {
        User owner = requireUser(userId);

        Memory memory = Memory.create(owner, MemoryType.TEXT, MemorySource.MANUAL, occurredAt(occurredAt));
        memory.describe(title, description, content);
        memory.markProcessed(ProcessingStatus.COMPLETED);

        Memory saved = memories.save(memory);
        embeddingJobs.enqueue(saved.getId(), userId);
        log.info("Created text memory {} for user {}", saved.getId(), userId);
        return saved;
    }

    /**
     * Validates, stores the bytes, records the metadata, and queues enrichment.
     * <p>
     * The file is written to object storage before the transaction commits, which means a rollback can
     * leave an object with no row pointing at it. That trade is deliberate: the alternative — committing
     * metadata for bytes that may not have landed — produces a memory the user can see but not open.
     * An orphaned object wastes space and nothing else, and is reclaimable by a sweep over the prefix.
     */
    @Transactional
    public Memory createUploadedMemory(UUID userId, MultipartFile file, String title, String description,
            Instant occurredAt) {
        User owner = requireUser(userId);
        ValidatedUpload upload = uploadValidator.validate(file);

        Memory memory = Memory.create(owner, upload.mediaType().memoryType(), MemorySource.UPLOAD,
                occurredAt(occurredAt), occurredAt != null);
        memory.describe(title, description, null);
        Memory saved = memories.save(memory);

        byte[] original = readAll(file);
        var displayable = heicToJpeg.toDisplayable(original, upload.fileName(), upload.mediaType());
        StorageKey key = StorageKey.forMemoryAsset(userId, saved.getId(), displayable.fileName());
        StoredObject stored = storage.store(key, new ByteArrayInputStream(displayable.bytes()),
                displayable.bytes().length);

        saved.attach(MediaAsset.of(key, displayable.fileName(), displayable.mediaType().mimeType(), stored.sizeBytes(),
                stored.checksum()));

        ProcessingJob job = jobs.save(
                ProcessingJob.pending(saved.getId(), userId, ProcessingJobType.MEDIA_METADATA));
        events.publishEvent(new ProcessingJobQueued(job.getId()));

        log.info("Created {} memory {} for user {} from upload {} ({} bytes)", saved.getType(), saved.getId(), userId,
                upload.fileName(), stored.sizeBytes());
        return saved;
    }

    @Transactional(readOnly = true)
    public Memory get(UUID userId, UUID memoryId) {
        return memories.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));
    }

    /**
     * Detail view: memory plus conversation messages, linked people, and face detections when present.
     */
    @Transactional(readOnly = true)
    public MemoryDetail getDetail(UUID userId, UUID memoryId) {
        Memory memory = get(userId, memoryId);
        List<ConversationMessage> messages = memory.getType() == MemoryType.CONVERSATION
                ? conversationMessages.findByMemoryIdOrderBySortIndexAsc(memory.getId())
                : List.of();
        List<Person> linkedPeople = personLinks.listForMemory(memory.getId());
        return new MemoryDetail(memory, messages, linkedPeople);
    }

    public record MemoryDetail(Memory memory, List<ConversationMessage> messages, List<Person> people) {
    }

    @Transactional
    public Person tagPerson(UUID userId, UUID memoryId, String displayName) {
        get(userId, memoryId);
        return personLinks.linkByDisplayName(userId, memoryId, displayName);
    }

    @Transactional
    public void untagPerson(UUID userId, UUID memoryId, UUID personId) {
        get(userId, memoryId);
        personLinks.unlink(userId, memoryId, personId);
    }

    @Transactional
    public Memory update(UUID userId, UUID memoryId, String title, String description, String content,
            Instant occurredAt) {
        Memory memory = get(userId, memoryId);
        memory.applyEdit(title, description, content, occurredAt);
        if (memory.getType() == MemoryType.TEXT && !StringUtils.hasText(memory.getContent())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A note needs some text");
        }
        embeddingJobs.enqueue(memory.getId(), userId);
        log.info("Updated memory {} for user {}", memoryId, userId);
        return memory;
    }

    @Transactional(readOnly = true)
    public Page<Memory> list(UUID userId, Pageable pageable) {
        return hydrate(memories.findTimelineIds(userId, pageable), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Memory> listWindow(UUID userId, Instant from, Instant to, Pageable pageable) {
        return hydrate(memories.findTimelineWindowIds(userId, from, to, pageable), pageable);
    }

    /**
     * Turns a page of ids into a page of fully loaded memories.
     * <p>
     * The graph has to be complete before this method returns: with {@code open-in-view} disabled the
     * persistence session closes at the transaction boundary, so anything the API layer reads later
     * would otherwise fail rather than quietly issue a query.
     */
    private Page<Memory> hydrate(Page<UUID> ids, Pageable pageable) {
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        return new PageImpl<>(memories.findAllWithAssets(ids.getContent()), pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MemoryStatsResponse stats(UUID userId) {
        return new MemoryStatsResponse(memories.countByUserId(userId),
                memories.findEarliestOccurrence(userId).orElse(null),
                people.countByUserId(userId),
                places.countByUserId(userId));
    }

    /**
     * Deletes the memory, its metadata and its files.
     * <p>
     * Files are removed after the rows, and a storage failure is logged rather than rethrown: the user
     * asked for the memory to be gone, and failing the request would leave them looking at something
     * they just deleted. Bytes with no row pointing at them are unreachable and collectable later.
     */
    @Transactional
    public void delete(UUID userId, UUID memoryId) {
        Memory memory = get(userId, memoryId);
        var keys = memory.getAssets().stream().map(MediaAsset::key).toList();

        memories.delete(memory);
        memories.flush();

        keys.forEach(key -> {
            try {
                storage.delete(key);
            } catch (RuntimeException ex) {
                log.error("Deleted memory {} but could not remove stored object {}", memoryId, key, ex);
            }
        });

        orphanCleanup.pruneForUser(userId);

        log.info("Deleted memory {} and {} asset(s) for user {}", memoryId, keys.size(), userId);
    }

    /**
     * Deletes every memory that occurred on the given calendar day in {@code zone}.
     *
     * @return number of memories removed
     */
    @Transactional
    public int deleteDay(UUID userId, LocalDate day, ZoneId zone) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<UUID> ids = memories.findIdsInOccurredWindow(userId, from, to);
        for (UUID id : ids) {
            deleteWithoutOrphanPrune(userId, id);
        }
        orphanCleanup.pruneForUser(userId);
        log.info("Deleted {} memories on {} ({}) for user {}", ids.size(), day, zone, userId);
        return ids.size();
    }

    /** Same as {@link #delete} but skips orphan prune (caller batches it). */
    private void deleteWithoutOrphanPrune(UUID userId, UUID memoryId) {
        Memory memory = get(userId, memoryId);
        var keys = memory.getAssets().stream().map(MediaAsset::key).toList();

        memories.delete(memory);
        memories.flush();

        keys.forEach(key -> {
            try {
                storage.delete(key);
            } catch (RuntimeException ex) {
                log.error("Deleted memory {} but could not remove stored object {}", memoryId, key, ex);
            }
        });
    }

    private byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Uploaded file could not be read", ex);
        }
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** An unspecified time means the memory is about now, which is the common case for a quick note. */
    private Instant occurredAt(Instant requested) {
        return requested != null ? requested : Instant.now();
    }
}
