package com.memorygraph.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores objects on the local filesystem. Intended for development and small self-hosted
 * deployments; an S3 or MinIO implementation replaces it by swapping
 * {@code memorygraph.storage.backend} with no change to callers.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "memorygraph.storage.backend", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileSystemStorageService implements StorageService {

    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final int COPY_BUFFER_BYTES = 16 * 1024;

    private final Path root;
    private final long maxFileSizeBytes;

    public LocalFileSystemStorageService(StorageProperties properties) {
        this.root = Path.of(properties.local().root()).toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    @PostConstruct
    void prepareRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new StorageException("Could not create storage root " + root, ex);
        }
        if (!Files.isWritable(root)) {
            throw new StorageException("Storage root is not writable: " + root);
        }
        log.info("Local object storage rooted at {}", root);
    }

    @Override
    public StoredObject store(StorageKey key, InputStream content, long sizeHint) {
        if (sizeHint > maxFileSizeBytes) {
            throw tooLarge();
        }

        Path target = resolve(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            // Written to a temporary file and moved into place, so a failure part-way through never
            // leaves a truncated object that looks complete.
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");

            MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            long written;
            try (OutputStream fileStream = Files.newOutputStream(temporary);
                    DigestOutputStream digestStream = new DigestOutputStream(fileStream, digest)) {
                written = copyWithLimit(content, digestStream);
            }

            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;

            return new StoredObject(key, written, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new StorageException("Could not store object " + key, ex);
        } finally {
            deleteQuietly(temporary);
        }
    }

    @Override
    public Resource retrieve(StorageKey key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new StorageException("Stored object is missing: " + key);
        }
        return new PathResource(path);
    }

    @Override
    public void delete(StorageKey key) {
        Path path = resolve(key);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new StorageException("Could not delete object " + key, ex);
        }
    }

    @Override
    public void deletePrefix(StorageKey prefix) {
        Path path = resolve(prefix);
        if (!Files.exists(path)) {
            return;
        }
        try {
            if (Files.isRegularFile(path)) {
                Files.deleteIfExists(path);
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                    try {
                        Files.deleteIfExists(entry);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            }
        } catch (IOException | UncheckedIOException ex) {
            throw new StorageException("Could not delete prefix " + prefix, ex);
        }
    }

    @Override
    public boolean exists(StorageKey key) {
        return Files.isRegularFile(resolve(key));
    }

    /**
     * Enforces the size limit while copying, because the declared content length of an upload cannot
     * be trusted.
     */
    private long copyWithLimit(InputStream source, OutputStream sink) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0;
        int read;
        while ((read = source.read(buffer)) != -1) {
            total += read;
            if (total > maxFileSizeBytes) {
                throw tooLarge();
            }
            sink.write(buffer, 0, read);
        }
        return total;
    }

    /**
     * Second line of defence behind {@link StorageKey}'s own validation: the resolved path is
     * confirmed to stay inside the storage root.
     */
    private Path resolve(StorageKey key) {
        Path resolved = root.resolve(key.value()).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not clean up temporary upload file {}", path, ex);
        }
    }

    private ApiException tooLarge() {
        return new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                "File exceeds the maximum size of %d bytes".formatted(maxFileSizeBytes));
    }
}
