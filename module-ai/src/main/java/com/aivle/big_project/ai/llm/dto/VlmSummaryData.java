package com.aivle.big_project.ai.llm.dto;

import java.util.List;

public record VlmSummaryData(
    int totalCount,
    int passCount,
    int rejectCount,
    int failedCount,
    int prevTotalCount,
    int prevRejectCount,
    List<VlmDefectCount> defects
) {}
