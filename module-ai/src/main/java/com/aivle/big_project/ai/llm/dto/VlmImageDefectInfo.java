package com.aivle.big_project.ai.llm.dto;

import java.util.List;

public record VlmImageDefectInfo(
    String imageType,
    List<String> defectType
) {}
