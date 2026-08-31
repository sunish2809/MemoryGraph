package com.memorygraph.backend.memory.application.upload;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.storage.StorageProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether an uploaded file may be stored at all.
 * <p>
 * Runs before anything is written, and rejects on three independent grounds: the file is empty, it is
 * larger than the configured ceiling, or its actual bytes are not a type we support. The size limit
 * is checked here for a fast, clear error and again while streaming to disk, because a declared
 * content length can lie.
 */
@Slf4j
@Component
public class UploadValidator {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String FALLBACK_FILE_NAME = "upload";

    private final long maxFileSizeBytes;

    public UploadValidator(StorageProperties properties) {
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No file was uploaded");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "File is %d bytes, which exceeds the maximum of %d".formatted(file.getSize(), maxFileSizeBytes));
        }

        SupportedMediaType mediaType = SupportedMediaType.detect(readHeader(file))
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported file type. Supported: JPEG, PNG, GIF, WebP, HEIC, MP4, MOV, WebM, MP3, WAV, M4A."));

        if (!mediaType.mimeType().equals(file.getContentType())) {
            // Not a failure: the browser is often simply wrong. Worth recording, since a systematic
            // mismatch can indicate someone probing the upload endpoint.
            log.debug("Upload declared content type {} but its bytes are {}", file.getContentType(),
                    mediaType.mimeType());
        }

        return new ValidatedUpload(mediaType, sanitiseFileName(file.getOriginalFilename()), file.getSize());
    }

    /**
     * Size and non-empty checks for a WhatsApp export (.txt / .zip). Type is validated when the
     * export is opened, not by image magic bytes.
     */
    public String validateImportPayload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No file was uploaded");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "File is %d bytes, which exceeds the maximum of %d".formatted(file.getSize(), maxFileSizeBytes));
        }
        return sanitiseFileName(file.getOriginalFilename());
    }

    /** Identifies an image from its own bytes (used when importing media out of a WhatsApp zip). */
    public SupportedMediaType requireImage(byte[] header) {
        return SupportedMediaType.detect(header)
                .filter(SupportedMediaType::isImage)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported file type inside WhatsApp export."));
    }

    /**
     * Keeps only a display name. Any directory component is dropped and control characters removed;
     * the value is never used to build a path, but it is rendered in the UI and returned in headers.
     */
    public String sanitiseFileName(String originalFileName) {
        String name = StringUtils.getFilename(originalFileName);
        if (!StringUtils.hasText(name)) {
            return FALLBACK_FILE_NAME;
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}\"\\\\/]", "").trim();
        if (cleaned.isEmpty()) {
            return FALLBACK_FILE_NAME;
        }
        return cleaned.length() > MAX_FILE_NAME_LENGTH ? cleaned.substring(0, MAX_FILE_NAME_LENGTH) : cleaned;
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return stream.readNBytes(SupportedMediaType.SIGNATURE_PROBE_BYTES);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Uploaded file could not be read", ex);
        }
    }
}
