package com.memorygraph.backend.memory.application.imports.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Opens a WhatsApp export (plain {@code .txt} or {@code .zip}) and yields the chat text plus any
 * media entries that can become photo memories.
 */
@Component
public class WhatsAppExportReader {

    private static final int MAX_ENTRY_BYTES = 40 * 1024 * 1024;

    public record OpenedExport(String chatText, String chatFileName, Map<String, byte[]> mediaByFileName) {
        public String checksum() {
            return sha256Hex(chatText.getBytes(StandardCharsets.UTF_8));
        }
    }

    public OpenedExport open(byte[] bytes, String originalFileName) throws IOException {
        String lower = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip") || looksLikeZip(bytes)) {
            return openZip(bytes, originalFileName);
        }
        if (lower.endsWith(".txt") || isMostlyText(bytes)) {
            return new OpenedExport(decodeText(bytes), originalFileName, Map.of());
        }
        throw new IllegalArgumentException("WhatsApp export must be a .txt chat or a .zip archive");
    }

    private OpenedExport openZip(byte[] bytes, String originalFileName) throws IOException {
        String chatText = null;
        String chatFileName = null;
        Map<String, byte[]> media = new LinkedHashMap<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entryName(entry.getName());
                // macOS resource-fork junk, not the real chat.
                if (baseName(name).startsWith("._")) {
                    continue;
                }
                byte[] content = readLimited(zip);
                if (isChatFile(name)) {
                    if (chatText == null || prefersChatFile(name, chatFileName)) {
                        chatText = decodeText(content);
                        chatFileName = name;
                    }
                } else if (isMediaFile(name)) {
                    media.putIfAbsent(baseName(name), content);
                    media.putIfAbsent(name, content);
                }
            }
        }

        if (chatText == null) {
            throw new IllegalArgumentException("Zip does not contain a WhatsApp chat .txt file");
        }
        String label = chatFileName != null ? chatFileName : originalFileName;
        return new OpenedExport(chatText, label, Map.copyOf(media));
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
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
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

    private static String entryName(String raw) {
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name;
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
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
        return bytes.length >= 4
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

    /** Resolves an attachment name against zip entries (exact, basename, or suffix match). */
    public static byte[] findMedia(Map<String, byte[]> mediaByFileName, String attachmentName) {
        if (!StringUtils.hasText(attachmentName) || mediaByFileName.isEmpty()) {
            return null;
        }
        byte[] exact = mediaByFileName.get(attachmentName);
        if (exact != null) {
            return exact;
        }
        String base = baseName(attachmentName);
        exact = mediaByFileName.get(base);
        if (exact != null) {
            return exact;
        }
        String lower = base.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, byte[]> entry : mediaByFileName.entrySet()) {
            if (baseName(entry.getKey()).toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static List<String> listMediaNames(Map<String, byte[]> mediaByFileName) {
        List<String> names = new ArrayList<>();
        for (String key : mediaByFileName.keySet()) {
            if (!key.contains("/")) {
                names.add(key);
            }
        }
        return names;
    }
}
