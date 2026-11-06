package com.venuenexus.common.pagination;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Common pagination query parameters.
 * Validates page boundaries, size limits, and per-endpoint sort whitelists.
 * Matches docs/api-contract.md §3.2.
 */
public record PageParams(
        Integer page,
        Integer size,
        String sortBy,
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    public PageParams {
        if (page == null) {
            page = DEFAULT_PAGE;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
        if (sortDirection == null || sortDirection.isBlank()) {
            sortDirection = DEFAULT_SORT_DIRECTION;
        }
    }

    public static PageParams of(Integer page, Integer size) {
        return new PageParams(page, size, null, DEFAULT_SORT_DIRECTION);
    }

    public static PageParams of(Integer page, Integer size, String sortBy, String sortDirection) {
        return new PageParams(page, size, sortBy, sortDirection);
    }

    public void validate(Set<String> allowedSortFields) {
        if (page < 0) {
            throw new ApiException(ErrorCode.PAGINATION_INVALID, "Page index must be greater than or equal to 0.");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.PAGINATION_INVALID, "Page size must be between 1 and 100 (inclusive).");
        }
        if (sortBy != null && !sortBy.isBlank()) {
            if (allowedSortFields != null && !allowedSortFields.isEmpty() && !allowedSortFields.contains(sortBy)) {
                throw new ApiException(ErrorCode.PAGINATION_INVALID,
                        "Sort field '" + sortBy + "' is not permitted. Permitted fields: " + allowedSortFields);
            }
            if (!"ASC".equalsIgnoreCase(sortDirection) && !"DESC".equalsIgnoreCase(sortDirection)) {
                throw new ApiException(ErrorCode.PAGINATION_INVALID,
                        "Sort direction must be either ASC or DESC.");
            }
        }
    }

    public Pageable toPageable(Set<String> allowedSortFields) {
        validate(allowedSortFields);
        if (sortBy != null && !sortBy.isBlank()) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(page, size, Sort.by(direction, sortBy));
        }
        return PageRequest.of(page, size);
    }
}
