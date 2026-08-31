package com.memorygraph.backend.memory.application.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns iPhone HEIC/HEIF into JPEG so the UI, captions, OCR and face detection can use the pixels.
 * Uses {@code heif-convert} (libheif) when present; otherwise the original bytes are kept.
 */
@Slf4j
@Component
public class HeicToJpegConverter {

    private static final String CONVERT_BIN = "heif-convert";
    private static final int QUALITY = 90;
    private static final int TIMEOUT_SECONDS = 45;
    private static final byte JPEG_SOI_0 = (byte) 0xFF;
    private static final byte JPEG_SOI_1 = (byte) 0xD8;

    private volatile Boolean available;

    public DisplayableImage toDisplayable(byte[] bytes, String fileName, SupportedMediaType detected) {
        if (detected != SupportedMediaType.HEIC) {
            return new DisplayableImage(bytes, fileName, detected);
        }
        byte[] jpeg = convert(bytes);
        if (jpeg == null) {
            log.warn("HEIC conversion unavailable or failed for {}; storing original", fileName);
            return new DisplayableImage(bytes, fileName, detected);
        }
        log.info("Converted HEIC {} to JPEG ({} → {} bytes)", fileName, bytes.length, jpeg.length);
        return new DisplayableImage(jpeg, jpegFileName(fileName), SupportedMediaType.JPEG);
    }

    public boolean looksLikeHeic(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        return mime.equals("image/heic") || mime.equals("image/heif");
    }

    static String jpegFileName(String original) {
        if (original == null || original.isBlank()) {
            return "photo.jpg";
        }
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) {
            return original.substring(0, original.length() - 5) + ".jpg";
        }
        return original + ".jpg";
    }

    private byte[] convert(byte[] heicBytes) {
        if (!isAvailable()) {
            return null;
        }
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("mg-heic-", ".heic");
            output = Files.createTempFile("mg-heic-", ".jpg");
            Files.write(input, heicBytes);

            Process process = startConvert(input, output, true);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("heif-convert timed out after {}s", TIMEOUT_SECONDS);
                return null;
            }
            if (process.exitValue() != 0) {
                String err = new String(process.getInputStream().readAllBytes());
                process = startConvert(input, output, false);
                finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return null;
                }
                if (process.exitValue() != 0) {
                    log.warn("heif-convert failed (exit {}): {}", process.exitValue(), err.strip());
                    return null;
                }
            }
            byte[] jpeg = Files.readAllBytes(output);
            if (jpeg.length < 2 || jpeg[0] != JPEG_SOI_0 || jpeg[1] != JPEG_SOI_1) {
                log.warn("heif-convert produced a file that is not JPEG");
                return null;
            }
            return jpeg;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("HEIC conversion failed: {}", ex.getMessage());
            return null;
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private static Process startConvert(Path input, Path output, boolean withQuality) throws IOException {
        ProcessBuilder builder = withQuality
                ? new ProcessBuilder(CONVERT_BIN, "-q", String.valueOf(QUALITY),
                        input.toAbsolutePath().toString(), output.toAbsolutePath().toString())
                : new ProcessBuilder(CONVERT_BIN, input.toAbsolutePath().toString(),
                        output.toAbsolutePath().toString());
        return builder.redirectErrorStream(true).start();
    }

    private boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (available != null) {
                return available;
            }
            try {
                Process probe = new ProcessBuilder(CONVERT_BIN)
                        .redirectErrorStream(true)
                        .start();
                probe.destroyForcibly();
                available = true;
            } catch (IOException ex) {
                available = false;
            }
            if (Boolean.FALSE.equals(available)) {
                log.warn("{} not found — HEIC files will be stored as-is (install libheif-tools)", CONVERT_BIN);
            } else {
                log.info("HEIC conversion enabled via {}", CONVERT_BIN);
            }
            return available;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // temp cleanup is best-effort
        }
    }
}
