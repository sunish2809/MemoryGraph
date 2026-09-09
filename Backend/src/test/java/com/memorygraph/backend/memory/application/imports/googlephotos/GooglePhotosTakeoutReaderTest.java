package com.memorygraph.backend.memory.application.imports.googlephotos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class GooglePhotosTakeoutReaderTest {

    private static final byte[] PNG = java.util.HexFormat.of().parseHex(
            "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C489"
                    + "0000000A49444154789C63000100000500010D0A2DB40000000049454E44AE426082");

    private final GooglePhotosTakeoutReader reader = new GooglePhotosTakeoutReader(JsonMapper.builder().build());

    @TempDir
    Path temp;

    @Test
    void requirePhotosAndOpenStoredReadSidecarWithoutHoldingEveryImage() throws Exception {
        Path zip = writeTakeout();
        reader.requirePhotos(zip);

        try (GooglePhotosTakeoutReader.OpenedTakeout opened = reader.openStored(zip)) {
            assertThat(opened.photos()).hasSize(1);
            GooglePhotosTakeoutReader.PhotoEntry photo = opened.photos().getFirst();
            assertThat(photo.fileName()).isEqualTo("a.png");
            assertThat(photo.takenAt()).isEqualTo(Instant.ofEpochSecond(1_723_700_000L));
            assertThat(photo.latitude()).isEqualTo(27.3);
            assertThat(photo.longitude()).isEqualTo(88.6);
            assertThat(opened.readPhoto(photo)).isEqualTo(PNG);
        }
    }

    @Test
    void requirePhotosRejectsZipWithNoImages() throws Exception {
        Path zip = temp.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("no photos".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertThatThrownBy(() -> reader.requirePhotos(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("photos");
    }

    private Path writeTakeout() throws Exception {
        Path zip = temp.resolve("takeout.zip");
        String sidecar = """
                {"photoTakenTime":{"timestamp":"1723700000"},"geoData":{"latitude":27.3,"longitude":88.6}}
                """;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("Takeout/Google Photos/Photos from 2024/a.png"));
            out.write(PNG);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("Takeout/Google Photos/Photos from 2024/a.png.json"));
            out.write(sidecar.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }
}
