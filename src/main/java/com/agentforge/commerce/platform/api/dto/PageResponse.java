package com.agentforge.commerce.platform.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope decoupled from Spring Data's internal page shape. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, List<T> mapped) {
        return new PageResponse<>(mapped, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
