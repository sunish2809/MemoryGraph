package com.memorygraph.backend.memory.application.imports.whatsapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Opens a WhatsApp export (plain {@code .txt} or {@code .zip}).
 * <p>
 * Media is never held as a whole-archive map. Upload validation reads only the chat {@code .txt};
 * processing opens the stored zip with {@link ZipFile} and extracts one attachment at a time.
 */
@Component
public class WhatsAppExportReader {

    private static final int MAX_ENTRY_BYTES = 40 * 1024 * 1024;
    private static final int MAGIC_BYTES = 4;

    public record ChatPreview(String chatText, String chatFileName) {
        public String checksum() {
            return sha256Hex(chatText.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Stored export ready for day-by-day import. Holds an open {@link ZipFile} when the payload is a
     * zip so photos can be pulled by name without inflating the archive into the heap.
     */
    public record OpenedExport(String chatText, String chatFileName, ZipFile zip, List<ZipEntry> mediaEntries)
            implements AutoCloseable {

        public String checksum() {
            return sha256Hex(chatText.getBytes(StandardCharsets.UTF_8));
        }

        /** Resolves an attachment name against zip entries (exact, basename, or suffix match). */
        public byte[] findMedia(String attachmentName) {
            if (zip == null || mediaEntries == null || mediaEntries.isEmpty()
                    || !StringUtils.hasText(attachmentName)) {
                return null;
            }
            ZipEntry match = resolveMediaEntry(attachmentName);
            if (match == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(match)) {
                return readLimited(in);
            } catch (IOException ex) {
                return null;
            }
        }

        private ZipEntry resolveMediaEntry(String attachmentName) {
            String base = baseName(attachmentName);
            String lower = base.toLowerCase(Locale.ROOT);
            for (ZipEntry entry : mediaEntries) {
                String name = entryName(entry.getName());
                if (name.equals(attachmentName) || baseName(name).equals(base)) {
                    return entry;
                }
            }
            for (ZipEntry entry : mediaEntries) {
                if (baseName(entryName(entry.getName())).toLowerCase(Locale.ROOT).equals(lower)) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            if (zip != null) {
                zip.close();
            }
        }
    }

    /**
     * Reads chat text only. Zip media entries are discarded as they stream past so a 250&nbsp;MB
     * export does not need 250&nbsp;MB of heap.
     */
    public ChatPreview readChatOnly(InputStream in, String originalFileName) throws IOException {
        String lower = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        PushbackInputStream pin = new PushbackInputStream(in, MAGIC_BYTES);
        byte[] magic = pin.readNBytes(MAGIC_BYTES);
        if (magic.length > 0) {
            pin.unread(magic);
        }
        if (lower.endsWith(".zip") || looksLikeZip(magic)) {
            return readChatFromZip(pin, originalFileName);
        }
        byte[] text = readLimited(pin);
        if (lower.endsWith(".txt") || isMostlyText(text)) {
            return new ChatPreview(decodeText(text), originalFileName);
        }
        throw new IllegalArgumentException("WhatsApp export must be a .txt chat or a .zip archive");
    }

    public OpenedExport openStored(Path path, String originalFileName) throws IOException {
        String lower = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        byte[] magic = readMagic(path);
        if (lower.endsWith(".zip") || looksLikeZip(magic)) {
            return openZipFile(path, originalFileName);
        }
        byte[] text;
        try (InputStream in = Files.newInputStream(path)) {
            text = readLimited(in);
        }
        if (lower.endsWith(".txt") || isMostlyText(text)) {
            return new OpenedExport(decodeText(text), originalFileName, null, List.of());
        }
        throw new IllegalArgumentException("WhatsApp export must be a .txt chat or a .zip archive");
    }

    private ChatPreview readChatFromZip(InputStream in, String originalFileName) throws IOException {
        String chatText = null;
        String chatFileName = null;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entryName(entry.getName());
                if (baseName(name).startsWith("._")) {
                    drain(zip);
                    continue;
                }
                if (isChatFile(name)) {
                    if (chatText == null || prefersChatFile(name, chatFileName)) {
                        chatText = decodeText(readLimited(zip));
                        chatFileName = name;
                    } else {
                        drain(zip);
                    }
                } else {
                    drain(zip);
                }
            }
        }
        if (chatText == null) {
            throw new IllegalArgumentException("Zip does not contain a WhatsApp chat .txt file");
        }
        String label = chatFileName != null ? chatFileName : originalFileName;
        return new ChatPreview(chatText, label);
    }

    private OpenedExport openZipFile(Path path, String originalFileName) throws IOException {
        ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8);
        try {
            List<ZipEntry> chatEntries = new ArrayList<>();
            List<ZipEntry> media = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entryName(entry.getName());
                if (baseName(name).startsWith("._")) {
                    continue;
                }
                if (isChatFile(name)) {
                    chatEntries.add(entry);
                } else if (isMediaFile(name)) {
                    media.add(entry);
                }
            }
            ZipEntry chosenChat = null;
            for (ZipEntry entry : chatEntries) {
                String name = entryName(entry.getName());
                if (chosenChat == null || prefersChatFile(name, entryName(chosenChat.getName()))) {
                    chosenChat = entry;
                }
            }
            if (chosenChat == null) {
                throw new IllegalArgumentException("Zip does not contain a WhatsApp chat .txt file");
            }
            String chatText;
            try (InputStream in = zip.getInputStream(chosenChat)) {
                chatText = decodeText(readLimited(in));
            }
            String label = entryName(chosenChat.getName());
            return new OpenedExport(chatText, label, zip, List.copyOf(media));
        } catch (IOException | RuntimeException | Error ex) {
            zip.close();
            throw ex;
        }
    }

    private static boolean prefersChatFile(String candidate, String current) {
        if (current == null) {
            return true;
        }
        String c = baseName(candidate).toLowerCase(Locale.ROOT);
        String cur = baseName(current).toLowerCase(Locale.ROOT);
        if (c.equals("_chat.txt") && !cur.equals("_chat.txt")) {
            return true;
        }
        return c.contains("whatsapp") && !cur.contains("whatsapp");
    }

    /** UTF-8 with BOM, falling back to UTF-16 when the export was saved that way. */
    static String decodeText(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
            return new String(bytes, StandardCharsets.UTF_16);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
            return new String(bytes, StandardCharsets.UTF_16);
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.startsWith("\ufeff")) {
            return utf8.substring(1);
        }
        return utf8;
    }

    private static boolean isChatFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String base = baseName(lower);
        return base.equals("_chat.txt")
                || base.endsWith(".txt") && (base.contains("whatsapp") || base.contains("chat"));
    }

    private static boolean isMediaFile(String name) {
        String lower = baseName(name).toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".heic");
    }

    static String entryName(String raw) {
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name;
    }

    static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static byte[] readMagic(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(MAGIC_BYTES);
        }
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        while (in.read(buffer) >= 0) {
            // discarded
        }
    }

    static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("Zip entry exceeds size limit");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean looksLikeZip(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K';
    }

    private static boolean isMostlyText(byte[] bytes) {
        int sample = Math.min(bytes.length, 512);
        int weird = 0;
        for (int i = 0; i < sample; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0) {
                return false;
            }
            if (b < 9 || (b > 13 && b < 32)) {
                weird++;
            }
        }
        return weird < sample / 10;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
