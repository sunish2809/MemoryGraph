package com.memorygraph.backend.memory.application.processing;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.ai.caption.ImageCaptionClient;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Appends a vision-model caption to searchable content when a chat provider is configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptionProcessor implements MemoryProcessor {

    private final StorageService storageService;
    private final ImageCaptionClient captions;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.CAPTION;
    }

    @Override
    public void process(Memory memory) {
        List<String> parts = new ArrayList<>();
        for (MediaAsset asset : memory.getAssets()) {
            try (InputStream stream = storageService.retrieve(asset.key()).getInputStream()) {
                byte[] bytes = stream.readAllBytes();
                captions.caption(bytes, asset.getMimeType()).ifPresent(parts::add);
            } catch (Exception ex) {
                log.warn("Caption skipped for asset {}: {}", asset.getId(), ex.toString());
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        String existing = memory.getContent() == null ? "" : memory.getContent().strip();
        String block = String.join("\n", parts).strip();
        memory.updateSearchableContent(StringUtils.hasText(existing) ? existing + "\n" + block : block);
    }
}
