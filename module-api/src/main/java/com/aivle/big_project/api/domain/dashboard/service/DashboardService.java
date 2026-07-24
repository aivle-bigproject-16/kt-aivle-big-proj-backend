package com.aivle.big_project.api.domain.dashboard.service;

import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.Request;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.Response;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.GraphData;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.GraphType;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final InspectionRepository inspectionRepository;
    private final DefectResultRepository defectResultRepository;

    public Response getDashboard(Request request){
        List<GraphData> graphData = switch (request.graphType()){
            case DAILY_TREND -> getDailyTrend(request);
            case DEFECT_TYPE -> getDefectType(request);
            case MANUFACTURE_DEFECTS -> getManufactureDefects(request);
        };

        return new Response(graphData);
    }

    private List<GraphData> getDailyTrend(Request request) {
        return inspectionRepository.findDailyRejectTrend(
                        request.startDate(),
                        request.todayDate(),
                        request.size()
                )
                .stream()
                .map(row -> new GraphData(
                        row[0].toString(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<GraphData> getDefectType(Request request){
        return defectResultRepository.findDefectResultType(
                        request.startDate(),
                        request.todayDate(),
                        request.size()
                )
                .stream()
                .map(row -> new GraphData(
                        row[0].toString(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<GraphData> getManufactureDefects(Request request){
        return inspectionRepository.findManufacturerRejectCounts(
                        request.startDate(),
                        request.todayDate(),
                        request.size()
                )
                .stream()
                .map(row -> new GraphData(
                        row[0].toString(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }
}
