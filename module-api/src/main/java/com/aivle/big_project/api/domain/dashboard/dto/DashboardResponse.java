package com.aivle.big_project.api.domain.dashboard.dto;

import java.util.List;

public record DashboardResponse (
        List<GraphData> graphData
){
}
