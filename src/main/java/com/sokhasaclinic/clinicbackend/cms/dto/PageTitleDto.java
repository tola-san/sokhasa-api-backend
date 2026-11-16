package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Summary representation of a page title in a specific language.
 */
public record PageTitleDto(
        String languageCode,
        String title
) {
}
