package com.aivle.big_project.api.domain.cell.dto;

import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.InspectionType;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record BatteryCellDetailResponse(
        Long batteryCellId,
        String cellSerialNo,
        String purchaseId,
        String productId,
        String modelName,
        String cellType,
        LocalDate manufacturedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<InspectionDto> inspections,
        List<ReportDto> reports
) {
    @Builder
    public record InspectionDto(
            Long batchId,
            List<Long> inspectionIds,
            FinalLabel finalLabel,
            LocalDateTime analyzedAt,
            List<InspectionImageDto> images,
            List<DefectResultDto> defectResults
    ) {}

    @Builder
    public record InspectionImageDto(
            Long imageId,
            Long inspectionId,
            InspectionType inspectionType,
            String imageType,
            String imageUrl
    ) {}

    @Builder
    public record DefectResultDto(
            Long defectResultId,
            Long inspectionId,
            Integer attemptNo,
            String label,
            Long imageId,
            String imageType,
            String defectType,
            String imageUrl,
            BigDecimal confidence,
            @JsonRawValue String bbox // JSON 문자열을 그대로 이스케이프 없이 반환
    ) {}

    @Builder
    public record ReportDto(
            Long reportId,
            Long inspectionId,
            String status,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
