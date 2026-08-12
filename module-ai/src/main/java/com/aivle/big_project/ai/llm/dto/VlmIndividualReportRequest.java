package com.aivle.big_project.ai.llm.dto;

import java.util.List;

public record VlmIndividualReportRequest(
        String cellSerialNo,
        Long inspectionId,
        Integer totalImages,
        List<Double> cellSize,
        List<List<Double>> pointGroups,
        Double ctVoidRatio,
        Double rgbDefectRate,
        List<VlmImageDefectInfo> defectInfo
) {

    public VlmIndividualReportRequest {
        validateCellSize(cellSize);
        validateRatio("ctVoidRatio", ctVoidRatio);
        validateRatio("rgbDefectRate", rgbDefectRate);

        if (cellSize != null) {
            cellSize = List.copyOf(cellSize);
        }

        pointGroups = pointGroups == null
                ? List.of()
                : pointGroups.stream()
                .map(List::copyOf)
                .toList();

        defectInfo = defectInfo == null
                ? List.of()
                : List.copyOf(defectInfo);
    }

    private static void validateCellSize(
            List<Double> cellSize
    ) {
        if (cellSize == null) {
            return;
        }

        if (cellSize.size() != 2) {
            throw new IllegalArgumentException(
                    "cellSize는 [widthMm, heightMm] "
                            + "형식의 길이 2 배열이어야 합니다."
            );
        }

        Double widthMm = cellSize.get(0);
        Double heightMm = cellSize.get(1);

        if (widthMm == null
                || !Double.isFinite(widthMm)
                || widthMm <= 0) {
            throw new IllegalArgumentException(
                    "cellSize의 widthMm은 0보다 큰 숫자여야 합니다."
            );
        }

        if (heightMm == null
                || !Double.isFinite(heightMm)
                || heightMm <= 0) {
            throw new IllegalArgumentException(
                    "cellSize의 heightMm은 0보다 큰 숫자여야 합니다."
            );
        }
    }

    private static void validateRatio(
            String fieldName,
            Double ratio
    ) {
        if (ratio == null) {
            return;
        }

        if (!Double.isFinite(ratio)
                || ratio < 0.0
                || ratio > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0.0~1.0 범위여야 합니다."
            );
        }
    }
}