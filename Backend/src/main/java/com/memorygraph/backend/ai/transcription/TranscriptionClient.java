package com.memorygraph.backend.ai.transcription;

import java.io.InputStream;

/**
 * Turns speech in an audio/video file into plain text for search and Ask.
 */
public interface TranscriptionClient {

    /**
     * @return transcript text, or empty when the provider cannot run (no key / unsupported)
     */
    String transcribe(String fileName, String mimeType, InputStream content, long sizeBytes);
}
