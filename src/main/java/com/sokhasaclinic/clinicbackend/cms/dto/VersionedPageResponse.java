package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Encapsulates a page detail with its optimistic lock revision number for ETag handling.
 */
public record VersionedPageResponse(
        PageDetailResponse data,
        long revision
) {
}
