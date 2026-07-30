package com.aivle.big_project.api.domain.report.dto;

import java.time.LocalDate;

public record DailyReportCreateRequest(LocalDate reportDate) {
}
