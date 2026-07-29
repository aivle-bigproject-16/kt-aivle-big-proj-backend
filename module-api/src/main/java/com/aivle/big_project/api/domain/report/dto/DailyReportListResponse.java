package com.aivle.big_project.api.domain.report.dto;

import com.aivle.big_project.domain.report.ReportsDaily;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record DailyReportListResponse(
        Long reportId,
        LocalDate reportDate,
        String status,
        String title,
        LocalDateTime createdAt
) {
    public static DailyReportListResponse from(ReportsDaily report) {
        return DailyReportListResponse.builder()
                .reportId(report.getId())
                .reportDate(report.getReportDate())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                .title(report.getTitle())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
