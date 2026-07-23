package com.aivle.big_project.api.domain.dashboard.controller;

import com.aivle.big_project.api.domain.dashboard.dto.DashboardRequest;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardResponse;
import com.aivle.big_project.api.domain.dashboard.service.DashboardService;
import com.aivle.big_project.api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private  final DashboardService dashboardService;

    @PostMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestBody DashboardRequest request
    ){
        if (request.startDate() == null || request.todayDate() == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<DashboardResponse>failure(
                            "INVALID_DATE",
                            "startDate와 todayDate는 필수입니다."
                    )
            );
        }

        if (request.startDate().isAfter(request.todayDate())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<DashboardResponse>failure(
                            "INVALID_DATE_RANGE",
                            "startDate는 todayDate보다 늦을 수 없습니다."
                    )
            );
        }

        DashboardResponse response = dashboardService.getDashboard(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "대시보드 기본 정보 조회가 완료되었습니다.",
                        response
                )
        );
    }
}
