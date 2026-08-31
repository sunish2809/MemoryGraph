package com.memorygraph.backend.memory.search;

/**
 * The markers wrapping matched words inside a snippet.
 * <p>
 * Highlighting is decided server-side because only the text search engine knows which words actually
 * matched: a search for "photos" matches the word "photo", and no amount of client-side string
 * comparison would find it.
 * <p>
 * Deliberately not {@code <mark>} tags. A snippet is assembled from the user's own text, and shipping
 * it as markup invites the client to render it as HTML, which turns every stored note into a possible
 * injection. Inert markers force the client to parse the snippet and build its own elements, so the
 * text can only ever be rendered as text.
 */
public final class SearchHighlight {

    public static final String START = "[[";
    public static final String END = "]]";

    private SearchHighlight() {
    }
}
