package com.aivle.big_project.domain.cell;

import com.aivle.big_project.domain.inspection.FinalLabel;
import java.time.LocalDateTime;

public interface BatteryCellWithLatestInspectionProjection {
    Long getInspectionId();
    Long getBatteryCellId();
    String getCellSerialNo();
    String getModelName();
    String getCellType();
    FinalLabel getLatestFinalLabel();
    LocalDateTime getLatestAnalyzedAt();
}
