package com.sokhasaclinic.clinicbackend.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ApiResponsePage<T>(
        List<T> data,
        PaginationMeta pagination
) {
    public static <T> ApiResponsePage<T> of(Page<T> page) {
        return new ApiResponsePage<>(page.getContent(), PaginationMeta.fromPage(page));
    }

    public static <T> ApiResponsePage<T> of(List<T> data, PaginationMeta pagination) {
        return new ApiResponsePage<>(data, pagination);
    }
}
