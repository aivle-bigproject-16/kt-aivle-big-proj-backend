package com.aivle.big_project.domain.cell;

import java.time.LocalDateTime;

public interface BatteryCellWithLatestInspectionProjection {
    Long getInspectionId();
    Long getBatteryCellId();
    Long getBatchId();
    String getLatestFinalLabel();
    LocalDateTime getLatestAnalyzedAt();
}
