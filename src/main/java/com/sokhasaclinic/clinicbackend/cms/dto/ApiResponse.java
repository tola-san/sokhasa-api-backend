package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Standard API response wrapper enclosing payload in a data field.
 */
public record ApiResponse<T>(
        T data
) {
}
