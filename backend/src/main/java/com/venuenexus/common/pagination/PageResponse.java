package com.venuenexus.common.pagination;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * Standard paginated response envelope.
 * Matches docs/api-contract.md §3.2.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResponse {
        items = (items != null) ? List.copyOf(items) : Collections.emptyList();
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int computedPages = (size > 0) ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(items, page, size, totalElements, computedPages);
    }

    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }
}
