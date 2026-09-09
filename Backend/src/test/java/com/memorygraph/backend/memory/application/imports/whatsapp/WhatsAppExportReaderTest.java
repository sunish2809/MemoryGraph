package com.memorygraph.backend.memory.application.imports.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class WhatsAppExportReaderTest {

    private static final byte[] PNG = hex(
            "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C489"
                    + "0000000A49444154789C63000100000500010D0A2DB40000000049454E44AE426082");

    private final WhatsAppExportReader reader = new WhatsAppExportReader();

    @TempDir
    Path temp;

    @Test
    void readChatOnlyKeepsChatAndDiscardsMedia() throws Exception {
        byte[] zip = zipWithChatAndPhoto();
        WhatsAppExportReader.ChatPreview preview = reader.readChatOnly(
                new ByteArrayInputStream(zip), "WhatsApp Chat.zip");

        assertThat(preview.chatText()).contains("Sikkim");
        assertThat(preview.chatFileName()).isEqualTo("WhatsApp Chat with Rahul.txt");
        assertThat(preview.checksum()).hasSize(64);
    }

    @Test
    void openStoredFindsOnePhotoByAttachmentNameWithoutKeepingTheArchive() throws Exception {
        Path zip = temp.resolve("export.zip");
        Files.write(zip, zipWithChatAndPhoto());

        try (WhatsAppExportReader.OpenedExport opened = reader.openStored(zip, "export.zip")) {
            assertThat(opened.chatText()).contains("Sikkim");
            byte[] photo = opened.findMedia("IMG-20240815-WA0001.jpg");
            assertThat(photo).isEqualTo(PNG);
            assertThat(opened.findMedia("missing.jpg")).isNull();
        }
    }

    @Test
    void rejectsZipWithoutChat() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("photo.jpg"));
            zip.write(PNG);
            zip.closeEntry();
        }
        assertThatThrownBy(() -> reader.readChatOnly(new ByteArrayInputStream(out.toByteArray()), "x.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chat");
    }

    private byte[] zipWithChatAndPhoto() throws Exception {
        byte[] chat = new ClassPathResource("fixtures/whatsapp/sample-chat.txt").getContentAsByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("WhatsApp Chat with Rahul.txt"));
            zip.write(chat);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Media/IMG-20240815-WA0001.jpg"));
            zip.write(PNG);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] hex(String hex) {
        return java.util.HexFormat.of().parseHex(hex);
    }
}
