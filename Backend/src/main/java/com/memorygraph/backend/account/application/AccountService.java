package com.memorygraph.backend.account.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.memorygraph.backend.account.api.dto.PrivacyStatusResponse;
import com.memorygraph.backend.auth.application.AuthService;
import com.memorygraph.backend.common.config.FacesProperties;
import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.ConversationMessageRepository;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.Person;
import com.memorygraph.backend.memory.domain.PersonRepository;
import com.memorygraph.backend.memory.domain.Place;
import com.memorygraph.backend.memory.domain.PlaceRepository;
import com.memorygraph.backend.storage.StorageKey;
import com.memorygraph.backend.storage.StorageProperties;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Account-level privacy, export and wipe. Memories CASCADE from {@code users}, so deleting the user
 * row is enough for the database; object storage is wiped by prefix afterwards.
 */
@Slf4j
@Service
public class AccountService {

    private static final String ARCHIVE_FORMAT = "memorygraph-archive-v1";
    private static final String DELETE_CONFIRMATION = "DELETE";
    private static final int EXPORT_BATCH = 100;

    private final UserRepository users;
    private final MemoryRepository memories;
    private final PersonRepository people;
    private final PlaceRepository places;
    private final ImportJobRepository imports;
    private final ConversationMessageRepository messages;
    private final StorageService storage;
    private final AuthService auth;
    private final JsonMapper json;
    private final FacesProperties faces;
    private final StorageProperties storageProperties;
    private final String chatProvider;
    private final String embeddingProvider;
    private final String chatModel;

    public AccountService(
            UserRepository users,
            MemoryRepository memories,
            PersonRepository people,
            PlaceRepository places,
            ImportJobRepository imports,
            ConversationMessageRepository messages,
            StorageService storage,
            AuthService auth,
            JsonMapper json,
            FacesProperties faces,
            StorageProperties storageProperties,
            @Value("${spring.ai.model.chat:none}") String chatProvider,
            @Value("${spring.ai.model.embedding:none}") String embeddingProvider,
            @Value("${memorygraph.ai.chat-model:none}") String chatModel) {
        this.users = users;
        this.memories = memories;
        this.people = people;
        this.places = places;
        this.imports = imports;
        this.messages = messages;
        this.storage = storage;
        this.auth = auth;
        this.json = json;
        this.faces = faces;
        this.storageProperties = storageProperties;
        this.chatProvider = chatProvider;
        this.embeddingProvider = embeddingProvider;
        this.chatModel = chatModel;
    }

    @Transactional(readOnly = true)
    public PrivacyStatusResponse privacy() {
        boolean chatOn = "openai".equalsIgnoreCase(chatProvider);
        boolean embeddingsOn = "openai".equalsIgnoreCase(embeddingProvider);
        return new PrivacyStatusResponse(
                true,
                storageProperties.backend().name(),
                chatOn,
                chatOn ? chatModel : "none",
                embeddingsOn,
                faces.enabled(),
                faces.enabled());
    }

    /**
     * Writes a zip of {@code archive.json} plus media files. The JSON is built first so the zip
     * stream does not hold a database transaction open for the duration of a large download.
     */
    public void exportArchive(UUID userId, OutputStream output) throws IOException {
        BuiltArchive built = buildArchive(userId);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("archive.json"));
            zip.write(json.writeValueAsBytes(built.document()));
            zip.closeEntry();

            for (PendingMedia file : built.files()) {
                try (InputStream in = storage.retrieve(file.key()).getInputStream()) {
                    zip.putNextEntry(new ZipEntry(file.zipPath()));
                    in.transferTo(zip);
                    zip.closeEntry();
                } catch (RuntimeException | IOException ex) {
                    log.warn("Skipping missing media {} while exporting account {}", file.key(), userId, ex);
                }
            }
        }
        log.info("Exported archive for user {} ({} memories)", userId, built.document().memories().size());
    }

    @Transactional
    public void deleteAccount(UUID userId, String password, String confirmation) {
        if (!DELETE_CONFIRMATION.equals(confirmation == null ? "" : confirmation.strip())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Type DELETE to confirm account deletion");
        }
        auth.verifyPassword(userId, password);

        users.deleteById(userId);
        users.flush();

        try {
            storage.deletePrefix(StorageKey.userPrefix(userId));
        } catch (RuntimeException ex) {
            log.error("Deleted account {} but could not wipe stored objects", userId, ex);
        }

        log.info("Deleted account {}", userId);
    }

    @Transactional(readOnly = true)
    BuiltArchive buildArchive(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Map<UUID, List<ConversationMessage>> messagesByMemory = messages
                .findByUserIdOrderByMemoryIdAscSortIndexAsc(userId)
                .stream()
                .collect(Collectors.groupingBy(ConversationMessage::getMemoryId));

        List<UUID> ids = memories.findAllIdsByUserId(userId);
        List<MemorySnapshot> memorySnapshots = new ArrayList<>(ids.size());
        List<PendingMedia> files = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += EXPORT_BATCH) {
            List<UUID> batch = ids.subList(from, Math.min(from + EXPORT_BATCH, ids.size()));
            for (Memory memory : memories.findAllWithAssets(batch)) {
                memorySnapshots.add(toSnapshot(
                        memory, messagesByMemory.getOrDefault(memory.getId(), List.of()), files));
            }
        }

        List<PersonSnapshot> personSnapshots = people.findAllForUser(userId).stream()
                .map(person -> new PersonSnapshot(person.getId(), person.getDisplayName(), person.getCreatedAt()))
                .toList();
        List<PlaceSnapshot> placeSnapshots = places.findAllForUser(userId).stream()
                .map(place -> new PlaceSnapshot(
                        place.getId(),
                        place.getDisplayName(),
                        place.getLatitude(),
                        place.getLongitude(),
                        place.getCreatedAt()))
                .toList();
        List<ImportSnapshot> importSnapshots = imports.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AccountService::toSnapshot)
                .toList();

        return new BuiltArchive(
                new ArchiveDocument(
                        ARCHIVE_FORMAT,
                        Instant.now(),
                        new AccountSnapshot(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt()),
                        memorySnapshots,
                        personSnapshots,
                        placeSnapshots,
                        importSnapshots),
                files);
    }

    private MemorySnapshot toSnapshot(
            Memory memory, List<ConversationMessage> chat, List<PendingMedia> files) {
        List<String> personNames = people.findByMemoryId(memory.getId()).stream()
                .map(Person::getDisplayName)
                .toList();
        List<String> placeNames = places.findByMemoryId(memory.getId()).stream()
                .map(Place::getDisplayName)
                .toList();
        List<AssetSnapshot> assets = new ArrayList<>();
        for (MediaAsset asset : memory.getAssets()) {
            String zipPath = "media/" + memory.getId() + "/" + asset.getId() + "-" + safeZipName(asset.getFileName());
            files.add(new PendingMedia(asset.key(), zipPath));
            assets.add(new AssetSnapshot(
                    asset.getId(),
                    asset.getFileName(),
                    asset.getMimeType(),
                    asset.getSizeBytes(),
                    asset.getChecksum(),
                    zipPath,
                    asset.getWidthPx(),
                    asset.getHeightPx(),
                    asset.getLatitude(),
                    asset.getLongitude(),
                    asset.getCapturedAt()));
        }
        List<MessageSnapshot> messageSnapshots = chat.stream()
                .map(line -> new MessageSnapshot(line.getSentAt(), line.getSenderName(), line.getBody()))
                .toList();
        return new MemorySnapshot(
                memory.getId(),
                memory.getType().name(),
                memory.getSource().name(),
                memory.getTitle(),
                memory.getDescription(),
                memory.getContent(),
                memory.getOccurredAt(),
                memory.getCreatedAt(),
                personNames,
                placeNames,
                assets,
                messageSnapshots);
    }

    private static ImportSnapshot toSnapshot(ImportJob job) {
        return new ImportSnapshot(
                job.getId(),
                job.getKind().name(),
                job.getStatus().name(),
                job.getFileName(),
                job.getChatName(),
                job.getMemoriesCreated(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }

    private static String safeZipName(String fileName) {
        String cleaned = fileName == null ? "" : fileName.replaceAll("[\\\\/]+", "_").replace("..", "_");
        String trimmed = cleaned.strip();
        return trimmed.isEmpty() ? "file" : trimmed;
    }

    record BuiltArchive(ArchiveDocument document, List<PendingMedia> files) {
    }

    record PendingMedia(StorageKey key, String zipPath) {
    }

    record ArchiveDocument(
            String format,
            Instant exportedAt,
            AccountSnapshot account,
            List<MemorySnapshot> memories,
            List<PersonSnapshot> people,
            List<PlaceSnapshot> places,
            List<ImportSnapshot> imports) {
    }

    record AccountSnapshot(UUID id, String email, String displayName, Instant createdAt) {
    }

    record MemorySnapshot(
            UUID id,
            String type,
            String source,
            String title,
            String description,
            String content,
            Instant occurredAt,
            Instant createdAt,
            List<String> people,
            List<String> places,
            List<AssetSnapshot> assets,
            List<MessageSnapshot> messages) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AssetSnapshot(
            UUID id,
            String fileName,
            String mimeType,
            long sizeBytes,
            String checksum,
            String zipPath,
            Integer widthPx,
            Integer heightPx,
            Double latitude,
            Double longitude,
            Instant capturedAt) {
    }

    record MessageSnapshot(Instant sentAt, String senderName, String body) {
    }

    record PersonSnapshot(UUID id, String displayName, Instant createdAt) {
    }

    record PlaceSnapshot(UUID id, String displayName, double latitude, double longitude, Instant createdAt) {
    }

    record ImportSnapshot(
            UUID id,
            String kind,
            String status,
            String fileName,
            String chatName,
            int memoriesCreated,
            String errorMessage,
            Instant createdAt,
            Instant finishedAt) {
    }
}
