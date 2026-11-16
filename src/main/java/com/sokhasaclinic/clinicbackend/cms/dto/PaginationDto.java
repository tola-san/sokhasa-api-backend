package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Standard pagination metadata DTO.
 */
public record PaginationDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
