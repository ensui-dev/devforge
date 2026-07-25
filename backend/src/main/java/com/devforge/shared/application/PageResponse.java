package com.devforge.shared.application;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Transport shape for paged results.
 *
 * <p>Spring's {@code Page} serialises to an unstable, deeply nested JSON
 * structure, so endpoints return this flat record instead and the client has one
 * pagination contract to code against.
 *
 * <p>Lives in the application layer, not an {@code api} package: services build
 * it, and controllers merely pass it through. (The architecture test flagged the
 * original placement — an application service must not depend on an api package.)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
