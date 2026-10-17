package com.smartcampus.common.dto;

import java.util.List;

/**
 * Standard pagination response wrapper.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
