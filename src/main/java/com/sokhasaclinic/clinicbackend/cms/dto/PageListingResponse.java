package com.sokhasaclinic.clinicbackend.cms.dto;

import java.util.List;

/**
 * Paginated response wrapper for CMS page summaries.
 */
public record PageListingResponse(
        List<PageSummaryResponse> data,
        PaginationDto pagination
) {
}
