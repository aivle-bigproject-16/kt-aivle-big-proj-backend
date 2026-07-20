package com.aivle.big_project.api.domain.dashboard.dto;

import java.time.LocalDate;

public record DashboardRequest (
    LocalDate todayDate,
    LocalDate startDate,
    int size,
    DashboardGraphType graphType
){

}
