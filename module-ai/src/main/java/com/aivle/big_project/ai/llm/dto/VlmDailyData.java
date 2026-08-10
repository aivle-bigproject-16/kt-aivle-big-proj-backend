package com.aivle.big_project.ai.llm.dto;

public record VlmDailyData(
    String reportDate,
    VlmSummaryData summaryData
) {}
