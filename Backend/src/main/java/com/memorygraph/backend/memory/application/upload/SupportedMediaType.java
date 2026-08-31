package com.memorygraph.backend.memory.application.upload;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.memorygraph.backend.memory.domain.MemoryType;

/**
 * The file types the application accepts, each tied to the memory type it becomes and to the leading
 * bytes that actually identify it.
 */
public enum SupportedMediaType {

    JPEG(MemoryType.PHOTO, "image/jpeg", "FFD8FF"),
    PNG(MemoryType.PHOTO, "image/png", "89504E470D0A1A0A"),
    GIF(MemoryType.PHOTO, "image/gif", "474946383961", "474946383761"),
    WEBP(MemoryType.PHOTO, "image/webp", "52494646"),
    HEIC(MemoryType.PHOTO, "image/heic"),

    MP4(MemoryType.VIDEO, "video/mp4"),
    MOV(MemoryType.VIDEO, "video/quicktime"),
    WEBM(MemoryType.VIDEO, "video/webm", "1A45DFA3"),

    MP3(MemoryType.AUDIO, "audio/mpeg", "494433", "FFFB", "FFF3", "FFF2"),
    WAV(MemoryType.AUDIO, "audio/wav", "52494646"),
    M4A(MemoryType.AUDIO, "audio/mp4");

    private static final int WEBP_MARKER_OFFSET = 8;
    private static final byte[] WEBP_MARKER = { 'W', 'E', 'B', 'P' };
    private static final int WAVE_MARKER_OFFSET = 8;
    private static final byte[] WAVE_MARKER = { 'W', 'A', 'V', 'E' };
    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis");
    private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "mp41", "mp42", "avc1", "dash");
    private static final Set<String> MOV_BRANDS = Set.of("qt  ");
    private static final Set<String> M4A_BRANDS = Set.of("M4A ", "M4B ");

    public static final int SIGNATURE_PROBE_BYTES = 16;

    private final MemoryType memoryType;
    private final String mimeType;
    private final byte[][] signatures;

    SupportedMediaType(MemoryType memoryType, String mimeType, String... hexSignatures) {
        this.memoryType = memoryType;
        this.mimeType = mimeType;
        this.signatures = Arrays.stream(hexSignatures).map(HexFormat.of()::parseHex).toArray(byte[][]::new);
    }

    public MemoryType memoryType() {
        return memoryType;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean isImage() {
        return memoryType == MemoryType.PHOTO;
    }

    /** WhatsApp stickers (and similar) — keep the file if uploaded, but do not run face detection. */
    public static boolean isWebp(String fileName, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/webp")) {
            return true;
        }
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".webp");
    }

    public boolean isAv() {
        return memoryType == MemoryType.AUDIO || memoryType == MemoryType.VIDEO;
    }

    /** Best-effort mapping when magic bytes are inconclusive (e.g. some Google CDN payloads). */
    public static Optional<SupportedMediaType> fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }
        String mime = mimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        return switch (mime) {
            case "image/jpeg", "image/jpg" -> Optional.of(JPEG);
            case "image/png" -> Optional.of(PNG);
            case "image/gif" -> Optional.of(GIF);
            case "image/webp" -> Optional.of(WEBP);
            case "image/heic", "image/heif" -> Optional.of(HEIC);
            case "video/mp4" -> Optional.of(MP4);
            case "video/quicktime" -> Optional.of(MOV);
            case "video/webm" -> Optional.of(WEBM);
            default -> Optional.empty();
        };
    }

    public static Optional<SupportedMediaType> detect(byte[] header) {
        String brand = majorBrand(header);
        if (brand != null) {
            if (HEIC_BRANDS.contains(brand)) {
                return Optional.of(HEIC);
            }
            if (MOV_BRANDS.contains(brand)) {
                return Optional.of(MOV);
            }
            if (M4A_BRANDS.contains(brand)) {
                return Optional.of(M4A);
            }
            if (MP4_BRANDS.contains(brand)) {
                return Optional.of(MP4);
            }
        }
        return Arrays.stream(values())
                .filter(candidate -> candidate != HEIC && candidate != MP4 && candidate != MOV && candidate != M4A)
                .filter(candidate -> candidate.matches(header))
                .findFirst();
    }

    private boolean matches(byte[] header) {
        boolean signatureMatches = Arrays.stream(signatures)
                .anyMatch(signature -> startsWith(header, signature, 0));
        if (!signatureMatches) {
            return false;
        }
        if (this == WEBP) {
            return startsWith(header, WEBP_MARKER, WEBP_MARKER_OFFSET);
        }
        if (this == WAV) {
            return startsWith(header, WAVE_MARKER, WAVE_MARKER_OFFSET);
        }
        return true;
    }

    private static String majorBrand(byte[] header) {
        if (header.length < 12 || !startsWith(header, FTYP, 4)) {
            return null;
        }
        return new String(header, 8, 4, StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] header, byte[] expected, int offset) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
