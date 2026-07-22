package com.aivle.big_project.api.domain.cell.dto;

import com.aivle.big_project.domain.cell.BatteryCell;
import lombok.Builder;
import java.time.LocalDate;

@Builder
public record BatteryCellListResponse(
        Long id,
        String cellSerialNo,
        String modelName,
        String cellType,
        LocalDate manufacturedDate
) {
    public static BatteryCellListResponse from(BatteryCell cell) {
        return BatteryCellListResponse.builder()
                .id(cell.getId())
                .cellSerialNo(cell.getCellSerialNo())
                .modelName(cell.getModelName())
                .cellType(cell.getCellType())
                .manufacturedDate(cell.getManufacturedDate())
                .build();
    }
}
