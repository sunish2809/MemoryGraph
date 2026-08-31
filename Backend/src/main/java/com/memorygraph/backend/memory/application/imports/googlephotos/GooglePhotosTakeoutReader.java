package com.memorygraph.backend.memory.application.imports.googlephotos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opens a Google Takeout zip of Google Photos and yields image bytes plus optional sidecar metadata.
 * <p>
 * Live Library API sync of a user's whole library is no longer available to third-party apps; Takeout
 * (or the Photos Picker for interactive selection) is the supported path.
 */
@Component
public class GooglePhotosTakeoutReader {

    private static final int MAX_ENTRY_BYTES = 80 * 1024 * 1024;
    private static final int MAX_PHOTOS = 5_000;

    private final JsonMapper json;

    public GooglePhotosTakeoutReader(JsonMapper json) {
        this.json = json;
    }

    public record PhotoEntry(
            String relativePath,
            String fileName,
            byte[] bytes,
            Instant takenAt,
            Double latitude,
            Double longitude) {
    }

    public record OpenedTakeout(List<PhotoEntry> photos, String checksum) {
    }

    public OpenedTakeout open(byte[] zipBytes, String originalFileName) throws IOException {
        String lower = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !looksLikeZip(zipBytes)) {
            throw new IllegalArgumentException("Google Photos Takeout must be a .zip archive");
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalisePath(entry.getName());
                if (baseName(name).startsWith("._") || name.contains("__MACOSX/")) {
                    continue;
                }
                files.put(name, readLimited(zip));
            }
        }

        List<PhotoEntry> photos = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            if (!isImagePath(path)) {
                continue;
            }
            if (photos.size() >= MAX_PHOTOS) {
                throw new IllegalArgumentException(
                        "Takeout contains more than " + MAX_PHOTOS + " photos; split the export or raise the limit");
            }
            SidecarMeta meta = readSidecar(files, path);
            photos.add(new PhotoEntry(path, baseName(path), file.getValue(), meta.takenAt(), meta.latitude(),
                    meta.longitude()));
        }

        if (photos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Zip does not contain any photos. Export Google Photos via Google Takeout and upload that zip.");
        }

        return new OpenedTakeout(List.copyOf(photos), sha256Hex(zipBytes));
    }

    private SidecarMeta readSidecar(Map<String, byte[]> files, String imagePath) {
        String[] candidates = {
                imagePath + ".json",
                replaceExtension(imagePath, ".json"),
                imagePath + ".supplemental-metadata.json"
        };
        for (String candidate : candidates) {
            byte[] raw = files.get(candidate);
            if (raw == null) {
                // Takeout sometimes uses the basename only under the same folder.
                raw = files.get(parentDir(imagePath) + baseName(candidate));
            }
            if (raw == null) {
                continue;
            }
            try {
                JsonNode root = json.readTree(raw);
                Instant taken = null;
                JsonNode photoTaken = root.path("photoTakenTime").path("timestamp");
                if (photoTaken.isTextual() || photoTaken.isNumber()) {
                    long epoch = Long.parseLong(photoTaken.asText());
                    taken = Instant.ofEpochSecond(epoch);
                }
                Double lat = null;
                Double lon = null;
                JsonNode geo = root.path("geoData");
                if (geo.isObject()) {
                    double gLat = geo.path("latitude").asDouble(0);
                    double gLon = geo.path("longitude").asDouble(0);
                    if (Math.abs(gLat) > 0.0001 || Math.abs(gLon) > 0.0001) {
                        lat = gLat;
                        lon = gLon;
                    }
                }
                return new SidecarMeta(taken, lat, lon);
            } catch (Exception ignored) {
                // Fall through to EXIF during enrichment.
            }
        }
        return new SidecarMeta(null, null, null);
    }

    private record SidecarMeta(Instant takenAt, Double latitude, Double longitude) {
    }

    private static boolean isImagePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".heic")
                || lower.endsWith(".heif");
    }

    private static String normalisePath(String name) {
        return name.replace('\\', '/');
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash + 1) : "";
    }

    private static String replaceExtension(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash) {
            return path.substring(0, dot) + newExt;
        }
        return path + newExt;
    }

    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("Zip entry exceeds " + MAX_ENTRY_BYTES + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
