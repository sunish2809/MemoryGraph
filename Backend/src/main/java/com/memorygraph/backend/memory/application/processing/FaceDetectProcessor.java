package com.memorygraph.backend.memory.application.processing;

import org.springframework.stereotype.Component;

import com.memorygraph.backend.memory.application.face.FaceService;
import com.memorygraph.backend.memory.domain.Memory;
import com.memorygraph.backend.memory.domain.ProcessingJobType;

import lombok.RequiredArgsConstructor;

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
        faceService.detectForMemory(memory);
    }
}
