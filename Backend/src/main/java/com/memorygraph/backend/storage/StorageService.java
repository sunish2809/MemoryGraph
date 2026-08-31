package com.memorygraph.backend.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Binary object storage. The only abstraction the application uses to read and write media, so the
 * backing store can be the local filesystem in development, MinIO in a self-hosted deployment, or
 * S3 in a managed one, without any caller changing.
 * <p>
 * Large files never pass through the database; PostgreSQL holds only the metadata that points here.
 */
public interface StorageService {

    /**
     * Writes an object and returns its recorded size and checksum. Implementations must make the
     * object visible only once it is completely written, so a crash mid-upload cannot leave a
     * truncated object that looks valid.
     *
     * @param content    consumed but not closed by the implementation; the caller owns the stream
     * @param sizeHint   expected byte count, used to reject oversized uploads early; may be negative
     *                   when unknown
     */
    StoredObject store(StorageKey key, InputStream content, long sizeHint);

    /** Opens an object for reading. Throws if it does not exist. */
    Resource retrieve(StorageKey key);

    /** Removes an object. Succeeds silently if it is already gone, so deletion is idempotent. */
    void delete(StorageKey key);

    /**
     * Removes every object under {@code prefix} (a user directory such as {@code users/{id}}).
     * Succeeds silently if the prefix is already empty, so account wipe is idempotent.
     */
    void deletePrefix(StorageKey prefix);

    boolean exists(StorageKey key);
}
