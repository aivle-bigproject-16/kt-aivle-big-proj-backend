package com.aivle.big_project.api.domain.report.dto;

import com.aivle.big_project.domain.report.ReportsIndividual;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record IndividualReportDetailResponse(
        Long reportId,
        Long batteryCellId,
        String cellSerialNo,
        String status,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ImageMappingDto> imageMappings
) {
    public static IndividualReportDetailResponse of(ReportsIndividual report, List<ImageMappingDto> imageMappings) {
        return IndividualReportDetailResponse.builder()
                .reportId(report.getId())
                .batteryCellId(report.getBatteryCell().getId())
                .cellSerialNo(report.getBatteryCell().getCellSerialNo())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                .title(report.getTitle())
                .content(report.getContent())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .imageMappings(imageMappings)
                .build();
    }
}
