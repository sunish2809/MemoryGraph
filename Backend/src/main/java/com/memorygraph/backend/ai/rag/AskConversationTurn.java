package com.memorygraph.backend.ai.rag;

/**
 * One prior Ask turn sent with a follow-up so retrieval and the model can resolve "that trip" / "the
 * cheaper one" against the conversation, not only the latest sentence.
 */
public record AskConversationTurn(String role, String content) {

    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }
}
