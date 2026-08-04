package com.aivle.big_project.api.domain.report.service;

import com.aivle.big_project.api.domain.report.dto.*;
import com.aivle.big_project.api.global.response.PagedResponse;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.report.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportsDailyRepository reportsDailyRepository;
    private final ReportsDailyItemRepository reportsDailyItemRepository;
    private final ReportsIndividualRepository reportsIndividualRepository;
    private final BatteryCellRepository batteryCellRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectResultRepository defectResultRepository;

    public PagedResponse<DailyReportListResponse> getDailyReports(Pageable pageable) {
        Page<ReportsDaily> reports = reportsDailyRepository.findAll(pageable);
        Page<DailyReportListResponse> responsePage = reports.map(DailyReportListResponse::from);
        return PagedResponse.from(responsePage);
    }

    public DailyReportDetailResponse getDailyReportDetail(Long id) {
        ReportsDaily report = reportsDailyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일일 리포트입니다."));

        return DailyReportDetailResponse.from(report);
    }

    public PagedResponse<IndividualReportListResponse> getIndividualReports(Pageable pageable) {
        Page<ReportsIndividual> reports = reportsIndividualRepository.findAll(pageable);
        Page<IndividualReportListResponse> responsePage = reports.map(IndividualReportListResponse::from);
        return PagedResponse.from(responsePage);
    }

    public IndividualReportDetailResponse getIndividualReportDetail(Long id) {
        ReportsIndividual report = reportsIndividualRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 개별 리포트입니다."));

        List<ImageMappingDto> imageMappings = new ArrayList<>();
        if (report.getRepresentativeInspection() != null) {
            List<DefectResult> defects = defectResultRepository.findByInspectionIdIn(List.of(report.getRepresentativeInspection().getId()));

            List<ImageMappingDto> ctMappings = defects.stream()
                    .filter(d -> "CT".equalsIgnoreCase(d.getImageType()) && d.getBbox() != null)
                    .limit(10)
                    .map(d -> ImageMappingDto.builder()
                            .imageType(d.getImageType())
                            .imageId(d.getInspectionImage() != null ? d.getInspectionImage().getId() : null)
                            .imgUrl(d.getInspectionImage() != null ? d.getInspectionImage().getObjectKey() : "")
                            .bbox(d.getBbox())
                            .build())
                    .toList();

            List<ImageMappingDto> rgbMappings = defects.stream()
                    .filter(d -> "RGB".equalsIgnoreCase(d.getImageType()) && d.getBbox() != null)
                    .limit(10)
                    .map(d -> ImageMappingDto.builder()
                            .imageType(d.getImageType())
                            .imageId(d.getInspectionImage() != null ? d.getInspectionImage().getId() : null)
                            .imgUrl(d.getInspectionImage() != null ? d.getInspectionImage().getObjectKey() : "")
                            .bbox(d.getBbox())
                            .build())
                    .toList();

            imageMappings.addAll(ctMappings);
            imageMappings.addAll(rgbMappings);
        }

        return IndividualReportDetailResponse.of(report, imageMappings);
    }

    @Transactional
    public DailyReportResponse createDailyReport(DailyReportCreateRequest request) {
        // 일일 리포트는 일자별 1건: 기존 데이터가 있으면 상태만 PENDING으로 변경, 없으면 새로 생성
        ReportsDaily report = reportsDailyRepository.findByReportDate(request.reportDate())
                .orElse(ReportsDaily.builder()
                        .reportDate(request.reportDate())
                        .status(ReportStatus.PENDING)
                        .build());
        
        if (report.getId() != null) {
            report.changeStatusToPending();
        }
        
        ReportsDaily saved = reportsDailyRepository.save(report);
        
        // module-ai (LLM 워커 서버)로 생성 비동기 처리 지시
        try {
            RestTemplate restTemplate = new RestTemplate();
            String aiServerUrl = "http://localhost:8081/internal/llm/reports/daily/" + saved.getId();
            restTemplate.postForEntity(aiServerUrl, null, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to trigger LLM generation for report " + saved.getId() + ": " + e.getMessage());
        }
        
        return DailyReportResponse.builder()
                .reportId(saved.getId())
                .reportDate(saved.getReportDate())
                .status(saved.getStatus().name())
                .build();
    }

    @Transactional
    public IndividualReportResponse createIndividualReport(IndividualReportCreateRequest request) {
        BatteryCell cell = batteryCellRepository.findById(request.batteryCellId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배터리 셀입니다."));

        // 최신 REJECT 검사 조회
        Inspection rejectInspection = inspectionRepository
                .findTopByBatteryCellIdAndFinalLabelOrderByCreatedAtDesc(cell.getId(), FinalLabel.REJECT)
                .orElse(null);

        ReportsIndividual newReport = ReportsIndividual.builder()
                .batteryCell(cell)
                .representativeInspection(rejectInspection) // 최근 REJECT 검사 매핑 (없으면 null)
                .status(ReportStatus.PENDING)
                .build();
        ReportsIndividual saved = reportsIndividualRepository.save(newReport);

        // 비동기 VLM 생성 지시 (module-ai 호출)
        try {
            RestTemplate restTemplate = new RestTemplate();
            String aiServerUrl = "http://localhost:8081/internal/llm/reports/individual/" + saved.getId();
            restTemplate.postForEntity(aiServerUrl, null, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to trigger LLM generation for individual report " + saved.getId() + ": " + e.getMessage());
        }
        
        return IndividualReportResponse.builder()
                .reportId(saved.getId())
                .batteryCellId(cell.getId())
                .status(saved.getStatus().name())
                .build();
    }
}
