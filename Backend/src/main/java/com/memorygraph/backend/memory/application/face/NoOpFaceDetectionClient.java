package com.memorygraph.backend.memory.application.face;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Used when faces are disabled or the HTTP client is not registered. */
@Component
@ConditionalOnMissingBean(FaceDetectionClient.class)
public class NoOpFaceDetectionClient implements FaceDetectionClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<DetectedFace> detect(byte[] imageBytes, String mimeType) {
        return List.of();
    }
}
