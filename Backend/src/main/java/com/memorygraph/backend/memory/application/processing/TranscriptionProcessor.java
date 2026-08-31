package com.memorygraph.backend.memory.application.processing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.memorygraph.backend.ai.transcription.TranscriptionClient;
import com.memorygraph.backend.memory.domain.MediaAsset;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.MemoryType;
import com.memorygraph.backend.memory.domain.ProcessingJobType;
import com.memorygraph.backend.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Appends a speech transcript to audio/video memories when a transcription provider is available.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionProcessor implements MemoryProcessor {

    private final StorageService storageService;
    private final TranscriptionClient transcriptionClient;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.TRANSCRIPTION;
    }

    @Override
    public void process(Memory memory) {
        if (memory.getType() != MemoryType.AUDIO && memory.getType() != MemoryType.VIDEO) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(memory.getContent())) {
            parts.add(memory.getContent().trim());
        }
        for (MediaAsset asset : memory.getAssets()) {
            try (InputStream in = storageService.retrieve(asset.key()).getInputStream()) {
                String text = transcriptionClient.transcribe(asset.getFileName(), asset.getMimeType(), in,
                        asset.getSizeBytes());
                if (StringUtils.hasText(text)) {
                    parts.add(text.trim());
                }
            } catch (IOException ex) {
                log.warn("Could not open asset {} for transcription", asset.getId(), ex);
            }
        }
        if (!parts.isEmpty()) {
            memory.updateSearchableContent(String.join("\n\n", parts));
        }
    }
}
