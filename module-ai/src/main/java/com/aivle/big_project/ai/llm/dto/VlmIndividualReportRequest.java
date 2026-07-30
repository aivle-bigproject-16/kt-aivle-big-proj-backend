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
) {}
