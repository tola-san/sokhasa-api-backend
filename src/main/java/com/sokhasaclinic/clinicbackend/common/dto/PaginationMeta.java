package com.sokhasaclinic.clinicbackend.common.dto;

import org.springframework.data.domain.Page;

public record PaginationMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static PaginationMeta fromPage(Page<?> page) {
        return new PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
