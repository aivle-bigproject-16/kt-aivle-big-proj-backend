package com.aivle.big_project.api.domain.report.dto;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record DailyReportResponse(
        Long reportId,
        LocalDate reportDate,
        String status
) {
}
