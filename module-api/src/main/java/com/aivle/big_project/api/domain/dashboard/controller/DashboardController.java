package com.aivle.big_project.api.domain.dashboard.controller;

import com.aivle.big_project.api.domain.dashboard.dto.DashboardRequest;
import com.aivle.big_project.api.domain.dashboard.dto.DashboardResponse;
import com.aivle.big_project.api.domain.dashboard.service.DashboardService;
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
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestBody DashboardRequest request
    ){
        DashboardResponse response = dashboardService.getDashboard(request);

        return ResponseEntity.ok(response);
    }
}
