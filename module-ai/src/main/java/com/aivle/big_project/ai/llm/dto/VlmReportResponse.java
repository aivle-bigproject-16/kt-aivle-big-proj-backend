package com.aivle.big_project.ai.llm.dto;

public record VlmReportResponse(
    String status,
    String title,
    String content,
    String failureReason
) {}
