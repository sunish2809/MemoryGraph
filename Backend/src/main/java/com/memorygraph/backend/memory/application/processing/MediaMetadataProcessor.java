package com.memorygraph.backend.memory.application.processing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.memory.application.PlaceLinkService;
import com.memorygraph.backend.memory.application.upload.HeicToJpegConverter;
import com.memorygraph.backend.memory.application.upload.ImageExifReader;
import com.memorygraph.backend.memory.application.upload.SupportedMediaType;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.storage.StorageService;
import com.memorygraph.backend.storage.StoredObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads what the stored file can tell us about itself, and turns the memory into something
 * searchable.
 * <p>
 * Dimensions (when ImageIO can decode the format), EXIF capture time / GPS, and a first-pass
 * searchable string from title, description and filename. OCR and captions append later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaMetadataProcessor implements MemoryProcessor {

    private final StorageService storageService;
    private final ImageExifReader exifReader;
    private final PlaceLinkService placeLinks;
    private final HeicToJpegConverter heicToJpeg;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.MEDIA_METADATA;
    }

    @Override
    public void process(Memory memory) {
        for (MediaAsset asset : memory.getAssets()) {
            convertHeicIfNeeded(asset);
            readImageDimensions(asset).ifPresent(
                    dimensions -> asset.recordImageDimensions(dimensions.width(), dimensions.height()));
            readExif(asset).ifPresent(exif -> {
                asset.recordCapture(exif.capturedAt(), exif.latitude(), exif.longitude());
                if (exif.capturedAt() != null) {
                    memory.moveTo(exif.capturedAt());
                }
                if (exif.latitude() != null && exif.longitude() != null) {
                    placeLinks.upsertAndLink(memory.getUserId(), memory.getId(), exif.latitude(),
                            exif.longitude());
                }
            });
        }
        memory.updateSearchableContent(buildSearchableContent(memory));
    }

    private record Dimensions(int width, int height) {
    }

    private void convertHeicIfNeeded(MediaAsset asset) {
        if (!heicToJpeg.looksLikeHeic(asset.getMimeType())) {
            return;
        }
        try (InputStream stream = storageService.retrieve(asset.key()).getInputStream()) {
            byte[] original = stream.readAllBytes();
            var displayable = heicToJpeg.toDisplayable(original, asset.getFileName(), SupportedMediaType.HEIC);
            if (displayable.mediaType() != SupportedMediaType.JPEG) {
                return;
            }
            StoredObject stored = storageService.store(asset.key(),
                    new ByteArrayInputStream(displayable.bytes()), displayable.bytes().length);
            asset.replacePayload(displayable.fileName(), displayable.mediaType().mimeType(), stored.sizeBytes(),
                    stored.checksum());
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not convert stored HEIC asset {}: {}", asset.getId(), ex.getMessage());
        }
    }

    /**
     * Absent rather than failed when the format has no reader available: WebP and HEIC often have no
     * ImageIO reader in a standard JDK. A missing dimension is not worth failing an upload over.
     */
    private Optional<Dimensions> readImageDimensions(MediaAsset asset) {
        try (InputStream stream = storageService.retrieve(asset.key()).getInputStream();
                ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {

            if (imageStream == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
            if (!readers.hasNext()) {
                log.debug("No image reader available for {} ({})", asset.getFileName(), asset.getMimeType());
                return Optional.empty();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageStream);
                return Optional.of(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            log.warn("Could not read image dimensions for asset {}", asset.getId(), ex);
            return Optional.empty();
        }
    }

    private Optional<ImageExifReader.ExifSummary> readExif(MediaAsset asset) {
        try (InputStream stream = storageService.retrieve(asset.key()).getInputStream()) {
            return exifReader.read(stream);
        } catch (IOException ex) {
            log.debug("Could not open asset {} for EXIF", asset.getId(), ex);
            return Optional.empty();
        }
    }

    /**
     * Everything worth matching on, in one string. Deliberately includes the original filename:
     * people name their files meaningfully ("sikkim-day2.jpg") and that is often the only text a
     * photo has until captioning / OCR exists.
     */
    private String buildSearchableContent(Memory memory) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, memory.getTitle());
        addIfPresent(parts, memory.getDescription());
        memory.getAssets().forEach(asset -> addIfPresent(parts, asset.getFileName()));
        return String.join("\n", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }
}
