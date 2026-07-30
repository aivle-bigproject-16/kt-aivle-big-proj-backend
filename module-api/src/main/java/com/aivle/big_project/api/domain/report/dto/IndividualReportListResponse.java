package com.aivle.big_project.api.domain.report.dto;

import com.aivle.big_project.domain.report.ReportsIndividual;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record IndividualReportListResponse(
        Long reportId,
        String status,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IndividualReportListResponse from(ReportsIndividual report) {
        return IndividualReportListResponse.builder()
                .reportId(report.getId())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                .title(report.getTitle())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
