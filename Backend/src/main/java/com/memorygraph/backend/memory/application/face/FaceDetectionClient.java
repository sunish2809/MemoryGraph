package com.memorygraph.backend.memory.application.face;

import java.util.List;

public interface FaceDetectionClient {

    boolean isAvailable();

    List<DetectedFace> detect(byte[] imageBytes, String mimeType);
}
