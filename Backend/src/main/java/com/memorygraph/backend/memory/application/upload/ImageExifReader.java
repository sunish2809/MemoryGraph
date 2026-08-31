package com.memorygraph.backend.memory.application.upload;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads capture time and GPS from image bytes (including HEIC containers) without decoding pixels.
 */
@Slf4j
@Component
public class ImageExifReader {

    public record ExifSummary(Instant capturedAt, Double latitude, Double longitude) {
    }

    public Optional<ExifSummary> read(InputStream stream) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(stream));
            Instant capturedAt = readCapturedAt(metadata).orElse(null);
            Double latitude = null;
            Double longitude = null;
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                GeoLocation location = gps.getGeoLocation();
                if (location != null && !location.isZero()) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                }
            }
            if (capturedAt == null && latitude == null) {
                return Optional.empty();
            }
            return Optional.of(new ExifSummary(capturedAt, latitude, longitude));
        } catch (ImageProcessingException | IOException | RuntimeException ex) {
            log.debug("No EXIF readable from stream: {}", ex.toString());
            return Optional.empty();
        }
    }

    private Optional<Instant> readCapturedAt(Metadata metadata) {
        ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (sub != null) {
            Date date = sub.getDateOriginal(TimeZone.getTimeZone(ZoneOffset.UTC));
            if (date != null) {
                return Optional.of(date.toInstant());
            }
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            Date date = ifd0.getDate(ExifIFD0Directory.TAG_DATETIME, TimeZone.getTimeZone(ZoneOffset.UTC));
            if (date != null) {
                return Optional.of(date.toInstant());
            }
        }
        return Optional.empty();
    }
}
