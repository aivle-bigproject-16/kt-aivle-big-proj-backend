package com.aivle.big_project.api.domain.cell.dto;

import com.aivle.big_project.domain.inspection.FinalLabel;
import java.time.LocalDateTime;

public record BatteryCellListResponse(
        Long inspectionId,
        Long batchId,
        Long batteryCellId,
        String cellSerialNo,
        String modelName,
        String cellType,
        FinalLabel latestFinalLabel,
        LocalDateTime latestAnalyzedAt
) {
}
