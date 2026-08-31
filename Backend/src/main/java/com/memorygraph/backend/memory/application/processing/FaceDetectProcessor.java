package com.memorygraph.backend.memory.application.processing;

import org.springframework.stereotype.Component;

import com.memorygraph.backend.memory.application.face.FaceService;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FaceDetectProcessor implements MemoryProcessor {

    private final FaceService faceService;

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.FACE_DETECT;
    }

    @Override
    public void process(Memory memory) {
        try {
            faceService.detectForMemory(memory);
        } catch (RuntimeException ex) {
            // Face detection is optional enrichment — never fail the memory.
            log.warn("Face detection failed for memory {}: {}", memory.getId(), ex.getMessage());
        }
    }
}
