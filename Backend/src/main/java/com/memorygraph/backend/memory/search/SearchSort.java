package com.memorygraph.backend.memory.search;

/** How a set of matching memories should be ordered. */
public enum SearchSort {

    /**
     * Best match first. Only meaningful when there is text to match against: with filters alone every
     * row scores identically, so {@link SearchCriteria} downgrades this to {@link #NEWEST} rather than
     * returning an arbitrary order and calling it relevance.
     */
    RELEVANCE,

    NEWEST,

    /** Useful for the question search is uniquely able to answer: when did I first mention this. */
    OLDEST
}
