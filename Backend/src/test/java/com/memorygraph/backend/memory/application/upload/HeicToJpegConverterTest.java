package com.memorygraph.backend.memory.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeicToJpegConverterTest {

    private final HeicToJpegConverter converter = new HeicToJpegConverter();

    @Test
    void jpegPassthroughKeepsBytesAndName() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 0x01};
        DisplayableImage out = converter.toDisplayable(jpeg, "holiday.jpg", SupportedMediaType.JPEG);
        assertThat(out.mediaType()).isEqualTo(SupportedMediaType.JPEG);
        assertThat(out.fileName()).isEqualTo("holiday.jpg");
        assertThat(out.bytes()).isEqualTo(jpeg);
    }

    @Test
    void heicWithoutConverterKeepsOriginal() {
        byte[] heic = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        DisplayableImage out = converter.toDisplayable(heic, "IMG_1234.HEIC", SupportedMediaType.HEIC);
        if (out.mediaType() == SupportedMediaType.HEIC) {
            assertThat(out.fileName()).isEqualTo("IMG_1234.HEIC");
            assertThat(out.bytes()).isEqualTo(heic);
        } else {
            assertThat(out.mediaType()).isEqualTo(SupportedMediaType.JPEG);
            assertThat(out.fileName()).isEqualTo("IMG_1234.jpg");
        }
    }

    @Test
    void jpegFileNameRewritesHeicAndHeif() {
        assertThat(HeicToJpegConverter.jpegFileName("IMG_1.HEIC")).isEqualTo("IMG_1.jpg");
        assertThat(HeicToJpegConverter.jpegFileName("shot.heif")).isEqualTo("shot.jpg");
        assertThat(HeicToJpegConverter.jpegFileName("noext")).isEqualTo("noext.jpg");
    }
}
