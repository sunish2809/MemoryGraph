package com.memorygraph.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

class LocalFileSystemStorageServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MEMORY_ID = UUID.randomUUID();

    /** SHA-256 of "hello", so the checksum is verified against a known value rather than itself. */
    private static final String HELLO_SHA_256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    Path storageRoot;

    private LocalFileSystemStorageService storage;

    @BeforeEach
    void setUp() {
        storage = newStorage(DataSize.ofKilobytes(64));
    }

    @Test
    void storesBytesAndReportsSizeAndChecksum() {
        StoredObject stored = store(storage, "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(stored.sizeBytes()).isEqualTo(5);
        assertThat(stored.checksum()).isEqualTo(HELLO_SHA_256);
        assertThat(storage.exists(stored.key())).isTrue();
    }

    @Test
    void returnsTheExactBytesThatWereStored() throws IOException {
        byte[] original = "a memory worth keeping".getBytes(StandardCharsets.UTF_8);
        StoredObject stored = store(storage, original);

        try (InputStream read = storage.retrieve(stored.key()).getInputStream()) {
            assertThat(read.readAllBytes()).isEqualTo(original);
        }
    }

    @Test
    void keepsEachUsersObjectsUnderTheirOwnPrefix() {
        StoredObject stored = store(storage, "private".getBytes(StandardCharsets.UTF_8));

        assertThat(storageRoot.resolve(stored.key().value())).exists();
        assertThat(stored.key().value()).startsWith("users/" + USER_ID);
    }

    @Test
    void deleteRemovesTheObjectAndIsSafeToRepeat() {
        StoredObject stored = store(storage, "temporary".getBytes(StandardCharsets.UTF_8));

        storage.delete(stored.key());
        assertThat(storage.exists(stored.key())).isFalse();

        storage.delete(stored.key());
        assertThat(storage.exists(stored.key())).isFalse();
    }

    @Test
    void deletePrefixRemovesEverythingUnderAUserAndLeavesOthers() {
        UUID otherUser = UUID.randomUUID();
        StoredObject mine = store(storage, "private".getBytes(StandardCharsets.UTF_8));
        StoredObject theirs = storage.store(
                StorageKey.forMemoryAsset(otherUser, MEMORY_ID, "photo.png"),
                new ByteArrayInputStream("theirs".getBytes(StandardCharsets.UTF_8)),
                6);

        storage.deletePrefix(StorageKey.userPrefix(USER_ID));

        assertThat(storage.exists(mine.key())).isFalse();
        assertThat(storage.exists(theirs.key())).isTrue();
        storage.deletePrefix(StorageKey.userPrefix(USER_ID));
    }

    @Test
    void retrievingAMissingObjectFails() {
        StorageKey missing = StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, "gone.png");

        assertThatThrownBy(() -> storage.retrieve(missing)).isInstanceOf(StorageException.class);
    }

    @Test
    void rejectsContentWhoseDeclaredSizeExceedsTheLimit() {
        LocalFileSystemStorageService tinyStorage = newStorage(DataSize.ofBytes(8));

        assertThatThrownBy(() -> store(tinyStorage, new byte[64]))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    /**
     * The declared length is only a claim. A caller that understates it must still be stopped, which is
     * why the limit is enforced again over the stream itself.
     */
    @Test
    void rejectsOversizedContentEvenWhenTheDeclaredSizeLies() {
        LocalFileSystemStorageService tinyStorage = newStorage(DataSize.ofBytes(8));

        assertThatThrownBy(() -> storeWithUnknownSize(tinyStorage, new byte[64]))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void leavesNoPartialFileBehindWhenAnUploadIsRejectedMidStream() throws IOException {
        LocalFileSystemStorageService tinyStorage = newStorage(DataSize.ofBytes(8));

        assertThatThrownBy(() -> storeWithUnknownSize(tinyStorage, new byte[64])).isInstanceOf(ApiException.class);

        try (var entries = Files.walk(storageRoot)) {
            assertThat(entries.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private LocalFileSystemStorageService newStorage(DataSize maxFileSize) {
        StorageProperties properties = new StorageProperties(StorageProperties.Backend.LOCAL, maxFileSize,
                new StorageProperties.Local(storageRoot.toString()));

        LocalFileSystemStorageService service = new LocalFileSystemStorageService(properties);
        service.prepareRoot();
        return service;
    }

    private StoredObject store(LocalFileSystemStorageService service, byte[] content) {
        return service.store(newKey(), new ByteArrayInputStream(content), content.length);
    }

    private StoredObject storeWithUnknownSize(LocalFileSystemStorageService service, byte[] content) {
        return service.store(newKey(), new ByteArrayInputStream(content), -1);
    }

    private StorageKey newKey() {
        return StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, "photo.png");
    }
}
