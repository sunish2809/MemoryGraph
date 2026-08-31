package com.memorygraph.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "memorygraph.storage")
public record StorageProperties(

        /** Which {@link StorageService} implementation to activate. */
        @NotNull Backend backend,

        /** Hard ceiling for a single uploaded file, enforced independently of the servlet limit. */
        @NotNull DataSize maxFileSize,

        @Valid @NotNull Local local) {

    public enum Backend {
        LOCAL
    }

    public record Local(
            /** Directory that holds every stored object. Must be writable by the application user. */
            @NotBlank String root) {
    }
}
