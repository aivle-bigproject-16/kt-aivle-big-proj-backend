package com.aivle.big_project.api.domain.dashboard.controller;

import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.Request;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.Response;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.GraphData;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardDto.GraphType;
import com.aivle.big_project.api.domain.dashboard.service.DashboardService;
import com.aivle.big_project.api.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void 시작일이_종료일보다_뒤면_400을_반환한다() {
        // given
        Request request = new Request(
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 22),
                5,
                GraphType.DAILY_TREND
        );

        // when
        ResponseEntity<ApiResponse<Response>> response =
                dashboardController.getDashboard(request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("INVALID_DATE_RANGE");

        // 날짜가 잘못됐으므로 Service/DB 조회가 일어나면 안 됨
        verifyNoInteractions(dashboardService);
    }
}