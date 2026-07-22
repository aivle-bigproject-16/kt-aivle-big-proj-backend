package com.aivle.big_project.api.domain.cell.dto;

import com.aivle.big_project.domain.cell.BatteryCell;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record BatteryCellDetailResponse(
        Long id,
        String cellSerialNo,
        String purchaseId,
        String productId,
        String modelName,
        String cellType,
        LocalDate manufacturedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BatteryCellDetailResponse from(BatteryCell cell) {
        return BatteryCellDetailResponse.builder()
                .id(cell.getId())
                .cellSerialNo(cell.getCellSerialNo())
                .purchaseId(cell.getPurchaseId())
                .productId(cell.getProductId())
                .modelName(cell.getModelName())
                .cellType(cell.getCellType())
                .manufacturedDate(cell.getManufacturedDate())
                .createdAt(cell.getCreatedAt())
                .updatedAt(cell.getUpdatedAt())
                .build();
    }
}
