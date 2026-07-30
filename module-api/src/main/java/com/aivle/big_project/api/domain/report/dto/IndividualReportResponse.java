package com.aivle.big_project.api.domain.report.dto;

import lombok.Builder;

@Builder
public record IndividualReportResponse(
        Long reportId,
        Long batteryCellId,
        String status
) {
}
