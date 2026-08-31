package com.memorygraph.backend.memory.application.imports.whatsapp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.common.error.ResourceNotFoundException;
import com.memorygraph.backend.common.time.ViewerZone;
import com.memorygraph.backend.memory.application.PersonLinkService;
import com.memorygraph.backend.memory.application.imports.ImportJobQueued;
import com.memorygraph.backend.memory.application.processing.EmbeddingJobEnqueuer;
import com.memorygraph.backend.memory.application.processing.ProcessingJobQueued;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.SupportedMediaType;
import com.memorygraph.backend.memory.application.upload.UploadValidator;
import com.memorygraph.backend.memory.domain.ConversationMessage;
import com.memorygraph.backend.memory.domain.ConversationMessageRepository;
import com.memorygraph.backend.memory.domain.ImportJob;
import com.memorygraph.backend.memory.domain.ImportJobRepository;
import com.memorygraph.backend.memory.domain.ImportJobStatus;
import com.memorygraph.backend.memory.domain.ImportKind;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryRepository;
import com.memorygraph.backend.memory.domain.MemorySource;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.ProcessingJob;
import com.memorygraph.backend.memory.domain.ProcessingJobRepository;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.memory.domain.ProcessingStatus;
import com.memorygraph.backend.storage.StorageKey;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;
import com.memorygraph.backend.user.domain.User;
import com.memorygraph.backend.user.domain.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WhatsAppImportService {

    private final ImportJobRepository importJobs;
    private final MemoryRepository memories;
    private final ConversationMessageRepository conversationMessages;
    private final ProcessingJobRepository processingJobs;
    private final UserRepository users;
    private final StorageService storage;
    private final UploadValidator uploadValidator;
    private final WhatsAppExportReader exportReader;
    private final WhatsAppChatParser chatParser;
    private final EmbeddingJobEnqueuer embeddingJobs;
    private final PersonLinkService personLinks;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final HeicToJpegConverter heicToJpeg;

    public WhatsAppImportService(
            ImportJobRepository importJobs,
            MemoryRepository memories,
            ConversationMessageRepository conversationMessages,
            ProcessingJobRepository processingJobs,
            UserRepository users,
            StorageService storage,
            UploadValidator uploadValidator,
            WhatsAppExportReader exportReader,
            WhatsAppChatParser chatParser,
            EmbeddingJobEnqueuer embeddingJobs,
            PersonLinkService personLinks,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            HeicToJpegConverter heicToJpeg) {
        this.importJobs = importJobs;
        this.memories = memories;
        this.conversationMessages = conversationMessages;
        this.processingJobs = processingJobs;
        this.users = users;
        this.storage = storage;
        this.uploadValidator = uploadValidator;
        this.exportReader = exportReader;
        this.chatParser = chatParser;
        this.embeddingJobs = embeddingJobs;
        this.personLinks = personLinks;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.heicToJpeg = heicToJpeg;
    }

    /**
     * Stores the export and queues async parsing. Re-uploading the same chat text returns the
     * existing job (retrying if it previously failed).
     */
    @Transactional
    public ImportJob startImport(UUID userId, MultipartFile file, String zone) {
        requireUser(userId);
        ViewerZone.parse(zone);
        String fileName = uploadValidator.validateImportPayload(file);

        byte[] bytes = readAll(file);
        WhatsAppExportReader.OpenedExport opened;
        try {
            opened = exportReader.open(bytes, fileName);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Could not read WhatsApp export", ex);
        }

        if (opened.chatText().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "WhatsApp chat file is empty");
        }

        String checksum = opened.checksum();
        ImportJob existing = importJobs.findByUserIdAndChecksum(userId, checksum).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ImportJobStatus.FAILED) {
                existing.resetForRetry();
                events.publishEvent(new ImportJobQueued(existing.getId()));
                log.info("Re-queued failed WhatsApp import {} for user {}", existing.getId(), userId);
            }
            return existing;
        }

        ImportJob job = ImportJob.pending(userId, ImportKind.WHATSAPP, fileName, checksum, zone);
        try {
            storage.store(job.key(), new ByteArrayInputStream(bytes), bytes.length);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not store WhatsApp export", ex);
        }

        ImportJob saved = importJobs.save(job);
        events.publishEvent(new ImportJobQueued(saved.getId()));
        log.info("Queued WhatsApp import {} for user {} ({} bytes)", saved.getId(), userId, bytes.length);
        return saved;
    }

    @Transactional(readOnly = true)
    public ImportJob get(UUID userId, UUID importId) {
        return importJobs.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job", importId));
    }

    /**
     * Parses the stored export and creates day-bucket conversation memories (and photo memories for
     * matched zip media). Each day commits in its own transaction so the timeline updates while the
     * import runs and the DB connection is not held for the whole chat.
     */
    public Result process(ImportJob job) throws IOException {
        Resource resource = storage.retrieve(job.key());
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }

        WhatsAppExportReader.OpenedExport opened = exportReader.open(bytes, job.getFileName());
        String preferredName = WhatsAppChatParser.chatNameFromFileName(
                opened.chatFileName() != null ? opened.chatFileName() : job.getFileName());
        WhatsAppChatParser.ParsedChat chat = chatParser.parse(opened.chatText(), preferredName);
        if (chat.messages().isEmpty()) {
            throw new IllegalArgumentException(
                    "No messages could be parsed from the WhatsApp export. First lines: "
                            + WhatsAppChatParser.previewLines(opened.chatText(), 3));
        }

        var zone = ViewerZone.parse(job.getZone());
        List<WhatsAppDayBucketer.DayBucket> days = WhatsAppDayBucketer.bucket(chat.messages());
        User owner = requireUser(job.getUserId());
        String selfDisplayName = owner.getDisplayName();
        int created = 0;
        Set<String> importedMedia = new HashSet<>();

        for (WhatsAppDayBucketer.DayBucket day : days) {
            Integer dayCreated = transactions.execute(status -> importOneDay(
                    job, owner, selfDisplayName, chat.chatName(), zone, day, opened, importedMedia));
            created += dayCreated != null ? dayCreated : 0;
        }

        return new Result(chat.chatName(), created);
    }

    private int importOneDay(
            ImportJob job,
            User owner,
            String selfDisplayName,
            String chatName,
            java.time.ZoneId zone,
            WhatsAppDayBucketer.DayBucket day,
            WhatsAppExportReader.OpenedExport opened,
            Set<String> importedMedia) {
        int created = 0;
        Memory conversation = Memory.create(owner, MemoryType.CONVERSATION, MemorySource.IMPORT,
                WhatsAppDayBucketer.occurredAt(day.firstMessageAt(), zone));
        conversation.describe(WhatsAppDayBucketer.title(chatName, day.day()),
                "Imported WhatsApp conversation", day.transcript());
        conversation.linkImport(job.getId());
        conversation.markProcessed(ProcessingStatus.COMPLETED);
        Memory saved = memories.save(conversation);
        int sortIndex = 0;
        Set<String> daySenders = new HashSet<>();
        for (WhatsAppChatParser.WhatsAppMessage message : day.messages()) {
            conversationMessages.save(ConversationMessage.of(
                    saved.getId(),
                    job.getUserId(),
                    WhatsAppDayBucketer.occurredAt(message.when(), zone),
                    message.sender(),
                    message.body(),
                    sortIndex++));
            daySenders.add(message.sender());
        }
        for (String sender : daySenders) {
            personLinks.upsertAndLink(job.getUserId(), saved.getId(), sender, selfDisplayName);
        }
        embeddingJobs.enqueue(saved.getId(), job.getUserId());
        created++;

        for (WhatsAppChatParser.WhatsAppMessage message : day.messages()) {
            for (String attachment : message.attachments()) {
                if (SupportedMediaType.isWebp(attachment, null)) {
                    continue;
                }
                if (!importedMedia.add(attachment.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                byte[] mediaBytes = WhatsAppExportReader.findMedia(opened.mediaByFileName(), attachment);
                if (mediaBytes == null || mediaBytes.length < SupportedMediaType.SIGNATURE_PROBE_BYTES) {
                    continue;
                }
                byte[] header = new byte[SupportedMediaType.SIGNATURE_PROBE_BYTES];
                System.arraycopy(mediaBytes, 0, header, 0, header.length);
                SupportedMediaType mediaType;
                try {
                    mediaType = uploadValidator.requireImage(header);
                } catch (ApiException ex) {
                    continue;
                }
                if (mediaType == SupportedMediaType.WEBP) {
                    continue;
                }
                Memory photo = Memory.create(owner, mediaType.memoryType(), MemorySource.IMPORT,
                        WhatsAppDayBucketer.occurredAt(message.when(), zone));
                String safeName = uploadValidator.sanitiseFileName(attachment);
                photo.describe(safeName, "From WhatsApp · " + chatName, null);
                photo.linkImport(job.getId());
                Memory savedPhoto = memories.save(photo);
                var displayable = heicToJpeg.toDisplayable(mediaBytes, safeName, mediaType);
                StorageKey assetKey = StorageKey.forMemoryAsset(job.getUserId(), savedPhoto.getId(),
                        displayable.fileName());
                StoredObject stored = storage.store(assetKey, new ByteArrayInputStream(displayable.bytes()),
                        displayable.bytes().length);
                savedPhoto.attach(MediaAsset.of(assetKey, displayable.fileName(),
                        displayable.mediaType().mimeType(), stored.sizeBytes(), stored.checksum()));
                personLinks.upsertAndLink(job.getUserId(), savedPhoto.getId(), message.sender(), selfDisplayName);
                ProcessingJob meta = processingJobs.save(
                        ProcessingJob.pending(savedPhoto.getId(), job.getUserId(), ProcessingJobType.MEDIA_METADATA));
                events.publishEvent(new ProcessingJobQueued(meta.getId()));
                created++;
            }
        }
        return created;
    }

    public record Result(String chatName, int memoriesCreated) {
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Uploaded file could not be read", ex);
        }
    }
}
