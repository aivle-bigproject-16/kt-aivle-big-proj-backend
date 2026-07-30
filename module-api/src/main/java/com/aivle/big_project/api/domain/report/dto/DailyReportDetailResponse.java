package com.aivle.big_project.api.domain.report.dto;

import com.aivle.big_project.domain.report.ReportsDaily;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record DailyReportDetailResponse(
        Long reportId,
        LocalDate reportDate,
        String status,
        String title,
        String content,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonRawValue String summary
) {
    public static DailyReportDetailResponse from(ReportsDaily report) {
        return DailyReportDetailResponse.builder()
                .reportId(report.getId())
                .reportDate(report.getReportDate())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                .title(report.getTitle())
                .content(report.getContent())
                .failureReason(report.getFailureReason())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .summary(report.getSummaryJson())
                .build();
    }
}
