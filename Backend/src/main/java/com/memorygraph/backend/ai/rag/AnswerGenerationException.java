package com.memorygraph.backend.ai.rag;

import com.memorygraph.backend.common.error.ApiException;
import com.memorygraph.backend.common.error.ErrorCode;

/** Raised when the LLM call fails after retrieval succeeded. */
public class AnswerGenerationException extends ApiException {

    public AnswerGenerationException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }
}
