package com.aivle.big_project.ai.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VlmDailyReportRequest(
    @JsonProperty("daily_data")
    VlmDailyData dailyData
) {}
