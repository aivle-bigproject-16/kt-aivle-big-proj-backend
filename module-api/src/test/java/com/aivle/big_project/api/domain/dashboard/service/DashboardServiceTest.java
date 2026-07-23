package com.aivle.big_project.api.domain.dashboard.service;

import com.aivle.big_project.api.domain.dashboard.dto.DashboardGraphType;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardRequest;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardResponse;
import com.aivle.big_project.api.domain.dashboard.dto.GraphData;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private DefectResultRepository defectResultRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void 날짜별_REJECT_추이를_조회한다() {
        // given: 가짜 Repository 결과를 준비
        DashboardRequest request = new DashboardRequest(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 14),
                5,
                DashboardGraphType.DAILY_TREND
        );

        List<Object[]> repositoryResult = Collections.singletonList(
                new Object[]{
                        Date.valueOf("2026-07-20"),
                        3L
                }
        );

        when(inspectionRepository.findDailyRejectTrend(
                eq(request.startDate()),
                eq(request.todayDate()),
                eq(request.size())
        )).thenReturn(repositoryResult);

        // when: Service 실행
        DashboardResponse response = dashboardService.getDashboard(request);

        // then: 결과 검증
        assertThat(response.graphData())
                .containsExactly(
                        new GraphData("2026-07-20", 3L)
                );
    }
}