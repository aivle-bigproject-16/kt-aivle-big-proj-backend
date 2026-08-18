package com.aivle.big_project.api.domain.report.controller;

import com.aivle.big_project.api.domain.report.dto.*;
import com.aivle.big_project.api.domain.report.service.ReportService;
import com.aivle.big_project.api.global.response.ApiResponse;
import com.aivle.big_project.api.global.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<PagedResponse<DailyReportListResponse>>> getDailyReports(
            @org.springframework.data.web.PageableDefault(sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        PagedResponse<DailyReportListResponse> response = reportService.getDailyReports(pageable);
        return ResponseEntity.ok(ApiResponse.success("일일 리포트 목록 조회가 완료되었습니다.", response));
    }

    @GetMapping("/daily/{id}")
    public ResponseEntity<ApiResponse<DailyReportDetailResponse>> getDailyReportDetail(@PathVariable Long id) {
        DailyReportDetailResponse response = reportService.getDailyReportDetail(id);
        return ResponseEntity.ok(ApiResponse.success("일일 리포트 상세 조회가 완료되었습니다.", response));
    }

    @PostMapping("/daily")
    public ResponseEntity<ApiResponse<DailyReportResponse>> createDailyReport(@RequestBody DailyReportCreateRequest request) {
        DailyReportResponse response = reportService.createDailyReport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("일일 리포트 생성 요청이 접수되었습니다.", response));
    }

    @GetMapping("/individual")
    public ResponseEntity<ApiResponse<PagedResponse<IndividualReportListResponse>>> getIndividualReports(
            @org.springframework.data.web.PageableDefault(sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        PagedResponse<IndividualReportListResponse> response = reportService.getIndividualReports(pageable);
        return ResponseEntity.ok(ApiResponse.success("개별 리포트 목록 조회가 완료되었습니다.", response));
    }

    @GetMapping("/individual/{id}")
    public ResponseEntity<ApiResponse<IndividualReportDetailResponse>> getIndividualReportDetail(@PathVariable Long id) {
        IndividualReportDetailResponse response = reportService.getIndividualReportDetail(id);
        return ResponseEntity.ok(ApiResponse.success("개별 리포트 상세 조회가 완료되었습니다.", response));
    }

    @PostMapping("/individual")
    public ResponseEntity<ApiResponse<IndividualReportResponse>> createIndividualReport(@RequestBody IndividualReportCreateRequest request) {
        IndividualReportResponse response = reportService.createIndividualReport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("개별 리포트 생성 요청이 접수되었습니다.", response));
    }

    @DeleteMapping("/daily/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDailyReport(@PathVariable Long id) {
        reportService.deleteDailyReport(id);
        return ResponseEntity.ok(ApiResponse.success("일일 리포트가 삭제되었습니다.", null));
    }

    @DeleteMapping("/individual/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIndividualReport(@PathVariable Long id) {
        reportService.deleteIndividualReport(id);
        return ResponseEntity.ok(ApiResponse.success("개별 리포트가 삭제되었습니다.", null));
    }
}
