package com.memorygraph.backend.memory.application.face;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Pulls a handful of stills from a video so InsightFace can run on ordinary JPEGs. Missing ffmpeg
 * is a no-op: face detection on video is enrichment, not a requirement to store the file.
 */
@Slf4j
@Component
public class VideoFrameExtractor {

    private static final int TIMEOUT_SECONDS = 90;

    public List<byte[]> extract(byte[] videoBytes, String mimeType, int maxFrames, int intervalSeconds) {
        if (videoBytes == null || videoBytes.length == 0 || maxFrames <= 0) {
            return List.of();
        }
        int interval = Math.max(1, intervalSeconds);
        Path dir = null;
        try {
            dir = Files.createTempDirectory("memorygraph-frames-");
            Path input = dir.resolve("input" + extensionFor(mimeType));
            Files.write(input, videoBytes);
            Path pattern = dir.resolve("face-frame-%03d.jpg");
            Process process = new ProcessBuilder(
                    "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                    "-i", input.toAbsolutePath().toString(),
                    "-vf", "fps=1/" + interval,
                    "-frames:v", String.valueOf(maxFrames),
                    "-q:v", "3",
                    pattern.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            try (InputStream stdout = process.getInputStream()) {
                stdout.readAllBytes();
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("ffmpeg timed out extracting video frames");
                return List.of();
            }
            if (process.exitValue() != 0) {
                log.warn("ffmpeg exited {} while extracting video frames", process.exitValue());
                return List.of();
            }
            List<Path> frames;
            try (Stream<Path> stream = Files.list(dir)) {
                frames = stream
                        .filter(path -> path.getFileName().toString().startsWith("face-frame-")
                                && path.getFileName().toString().endsWith(".jpg"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
            List<byte[]> jpegs = new ArrayList<>(frames.size());
            for (Path frame : frames) {
                jpegs.add(Files.readAllBytes(frame));
            }
            return jpegs;
        } catch (IOException ex) {
            log.warn("Could not extract video frames (is ffmpeg installed?): {}", ex.getMessage());
            return List.of();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            deleteRecursively(dir);
        }
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return ".mp4";
        }
        return switch (mimeType) {
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // temp cleanup
                }
            });
        } catch (IOException ignored) {
            // temp cleanup
        }
    }
}
