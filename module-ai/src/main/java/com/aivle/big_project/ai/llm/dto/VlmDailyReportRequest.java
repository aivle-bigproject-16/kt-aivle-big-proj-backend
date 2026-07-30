package com.aivle.big_project.ai.llm.dto;

public record VlmDailyReportRequest(
    String reportDate,
    VlmSummaryData summaryData
) {}
