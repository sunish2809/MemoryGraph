package com.memorygraph.backend.memory.application.imports.googlephotos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GooglePhotosImportServiceTest {

    @Test
    void albumHintUsesFolderUnderGooglePhotos() {
        assertThat(GooglePhotosImportService.albumFromPath("Takeout/Google Photos/Photos from 2024/a.jpg"))
                .isEqualTo("Photos from 2024");
    }

    @Test
    void albumHintFallsBackToParentFolder() {
        assertThat(GooglePhotosImportService.albumFromPath("Holiday/IMG_1.jpg")).isEqualTo("Holiday");
    }
}
