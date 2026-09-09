package com.memorygraph.backend.memory.application.imports.googlephotos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opens a Google Takeout zip of Google Photos and yields image paths plus optional sidecar metadata.
 * <p>
 * Live Library API sync of a user's whole library is no longer available to third-party apps; Takeout
 * (or the Photos Picker for interactive selection) is the supported path.
 * <p>
 * Image bytes stay on disk until each photo is imported. A 250&nbsp;MB zip is not inflated into the
 * heap.
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
            Instant takenAt,
            Double latitude,
            Double longitude) {
    }

    public record OpenedTakeout(ZipFile zip, Map<String, ZipEntry> entriesByPath, List<PhotoEntry> photos)
            implements AutoCloseable {

        public byte[] readPhoto(PhotoEntry photo) throws IOException {
            ZipEntry entry = entriesByPath.get(photo.relativePath());
            if (entry == null) {
                return new byte[0];
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return readLimited(in);
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    /** Rejects empty or oversized Takeouts without loading image bytes. */
    public void requirePhotos(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalisePath(entry.getName());
                if (baseName(name).startsWith("._") || name.contains("__MACOSX/")) {
                    continue;
                }
                if (isImagePath(name)) {
                    count++;
                    if (count > MAX_PHOTOS) {
                        throw new IllegalArgumentException(
                                "Takeout contains more than " + MAX_PHOTOS
                                        + " photos; split the export or raise the limit");
                    }
                }
            }
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Zip does not contain any photos. Export Google Photos via Google Takeout and upload that zip.");
            }
        }
    }

    public OpenedTakeout openStored(Path zipPath) throws IOException {
        ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8);
        try {
            Map<String, ZipEntry> index = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalisePath(entry.getName());
                if (baseName(name).startsWith("._") || name.contains("__MACOSX/")) {
                    continue;
                }
                index.put(name, entry);
            }

            List<PhotoEntry> photos = new ArrayList<>();
            for (String path : index.keySet()) {
                if (!isImagePath(path)) {
                    continue;
                }
                if (photos.size() >= MAX_PHOTOS) {
                    throw new IllegalArgumentException(
                            "Takeout contains more than " + MAX_PHOTOS + " photos; split the export or raise the limit");
                }
                SidecarMeta meta = readSidecar(zip, index, path);
                photos.add(new PhotoEntry(path, baseName(path), meta.takenAt(), meta.latitude(), meta.longitude()));
            }

            if (photos.isEmpty()) {
                throw new IllegalArgumentException(
                        "Zip does not contain any photos. Export Google Photos via Google Takeout and upload that zip.");
            }

            return new OpenedTakeout(zip, Map.copyOf(index), List.copyOf(photos));
        } catch (Exception | Error ex) {
            zip.close();
            throw ex;
        }
    }

    private SidecarMeta readSidecar(ZipFile zip, Map<String, ZipEntry> files, String imagePath) {
        String[] candidates = {
                imagePath + ".json",
                replaceExtension(imagePath, ".json"),
                imagePath + ".supplemental-metadata.json"
        };
        for (String candidate : candidates) {
            ZipEntry entry = files.get(candidate);
            if (entry == null) {
                entry = files.get(parentDir(imagePath) + baseName(candidate));
            }
            if (entry == null) {
                continue;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] raw = readLimited(in);
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

    static byte[] readLimited(InputStream in) throws IOException {
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
}
