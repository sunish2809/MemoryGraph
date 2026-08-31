package com.memorygraph.backend.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Pagination metadata alongside the items, so a client can render "page 2 of 7" and decide whether to
 * fetch more without a second call.
 * <p>
 * Deliberately not Spring's {@code Page}: its JSON shape is an implementation detail that has changed
 * between versions, and pinning our own contract keeps the API stable.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
