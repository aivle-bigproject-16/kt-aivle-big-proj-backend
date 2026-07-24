package com.aivle.big_project.api.domain.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDto {

    private DashboardDto() {
        // DTO 묶음 클래스는 객체로 만들 필요가 없습니다.
    }

    public record Request(
            LocalDate todayDate,
            LocalDate startDate,
            int size,
            GraphType graphType
    ) {
    }

    public enum GraphType {
        DEFECT_TYPE,
        DAILY_TREND,
        MANUFACTURE_DEFECTS
    }

    public record GraphData(
            String label,
            long value
    ) {
    }

    public record Response(
            List<GraphData> graphData
    ) {
    }
}