package com.memorygraph.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageKeyTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void namespacesObjectsByUserAndMemory() {
        StorageKey key = StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, "sunset.jpg");

        assertThat(key.value())
                .startsWith("users/%s/memories/%s/".formatted(USER_ID, MEMORY_ID))
                .endsWith(".jpg");
    }

    @Test
    void discardsTheClientFilenameSoOnlyTheExtensionSurvives() {
        StorageKey key = StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, "family photo (1).JPEG");

        assertThat(key.value()).doesNotContain("family", "photo", " ", "(").endsWith(".jpeg");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "../../secrets.png",
            "photo.png/../../../root/.ssh/id_rsa"
    })
    void aTraversalAttemptInTheFilenameCannotProduceATraversingKey(String hostileFileName) {
        StorageKey key = StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, hostileFileName);

        assertThat(key.value()).doesNotContain("..");
    }

    @Test
    void rejectsATraversingKeyBuiltDirectly() {
        assertThatThrownBy(() -> new StorageKey("users/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnAbsoluteKey() {
        assertThatThrownBy(() -> new StorageKey("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankKey() {
        assertThatThrownBy(() -> new StorageKey("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dropsAnImplausiblyLongExtension() {
        StorageKey key = StorageKey.forMemoryAsset(USER_ID, MEMORY_ID, "payload.thisisnotanextension");

        assertThat(key.value()).doesNotContain(".thisisnotanextension");
    }
}
