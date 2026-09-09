package com.memorygraph.backend.ai.rag;

/**
 * This account has already used its monthly language-model Ask allowance. Ask should list retrieved
 * memories instead of calling OpenAI.
 */
public class AskQuotaExceededException extends AnswerGenerationException {

    public AskQuotaExceededException() {
        super("This account has used its monthly AI Ask allowance.", null);
    }
}
