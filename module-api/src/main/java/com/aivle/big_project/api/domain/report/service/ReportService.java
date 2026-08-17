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
import com.aivle.big_project.api.global.storage.S3ImageUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.web.client.RestClient;

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
    private final RestClient aiGatewayRestClient;
    private final S3ImageUrlService s3ImageUrlService;

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
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 개별 리포트입니다.")
                );

        Inspection representativeInspection = report.getRepresentativeInspection();

        if (representativeInspection == null) {
            return IndividualReportDetailResponse.of(report, List.of());
        }

        List<Long> inspectionIds;

        /*
         * 과거 데이터 등에서 배치 연결이 없는 경우에는
         * 기존 방식대로 대표 Inspection 한 건만 조회합니다.
         */
        if (representativeInspection.getInspectionBatch() == null
                || representativeInspection.getBatteryCell() == null) {
            inspectionIds = List.of(representativeInspection.getId());
        } else {
            Long inspectionBatchId =
                    representativeInspection.getInspectionBatch().getId();

            Long batteryCellId =
                    representativeInspection.getBatteryCell().getId();

            // 같은 셀·같은 배치의 CT/RGB Inspection 모두 조회
            inspectionIds = inspectionRepository
                    .findByInspectionBatchIdAndBatteryCellIdOrderByIdAsc(
                            inspectionBatchId,
                            batteryCellId
                    )
                    .stream()
                    .map(Inspection::getId)
                    .toList();
        }

        List<DefectResult> defects =
                defectResultRepository.findByInspectionIdIn(inspectionIds);

        // 기존과 동일하게 CT 최대 10건
        List<ImageMappingDto> ctMappings = defects.stream()
                .filter(defect ->
                        "CT".equalsIgnoreCase(defect.getImageType())
                                && defect.getBbox() != null
                                && defect.getInspectionImage() != null
                )
                .limit(10)
                .map(this::toImageMapping)
                .toList();

        // 기존과 동일하게 RGB 최대 10건
        List<ImageMappingDto> rgbMappings = defects.stream()
                .filter(defect ->
                        "RGB".equalsIgnoreCase(defect.getImageType())
                                && defect.getBbox() != null
                                && defect.getInspectionImage() != null
                )
                .limit(10)
                .map(this::toImageMapping)
                .toList();

        List<ImageMappingDto> imageMappings = new ArrayList<>();
        imageMappings.addAll(ctMappings);
        imageMappings.addAll(rgbMappings);

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
        
        dispatchAfterCommit(
                "/internal/llm/reports/daily/{reportId}",
                saved.getId(),
                "report"
        );
        
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

        Integer maxVersion = reportsIndividualRepository.findMaxVersionByBatteryCellId(cell.getId());
        int nextVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        ReportsIndividual newReport = ReportsIndividual.builder()
                .batteryCell(cell)
                .version(nextVersion)
                .representativeInspection(rejectInspection) // 최근 REJECT 검사 매핑 (없으면 null)
                .status(ReportStatus.PENDING)
                .build();
        ReportsIndividual saved = reportsIndividualRepository.save(newReport);

        dispatchAfterCommit(
                "/internal/llm/reports/individual/{reportId}",
                saved.getId(),
                "individual report"
        );
        
        return IndividualReportResponse.builder()
                .reportId(saved.getId())
                .batteryCellId(cell.getId())
                .status(saved.getStatus().name())
                .build();
    }

    private void dispatchAfterCommit(String uri, Long reportId, String reportType) {
        Runnable dispatch = () -> {
            try {
                aiGatewayRestClient.post()
                        .uri(uri, reportId)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                System.err.println("Failed to trigger LLM generation for " + reportType + " " + reportId + ": " + e.getMessage());
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }

    private ImageMappingDto toImageMapping(DefectResult defect) {
        String imageUrl = null;

        if (defect.getInspectionImage() != null) {
            imageUrl = s3ImageUrlService.createGetUrl(
                    defect.getInspectionImage().getBucketName(),
                    defect.getInspectionImage().getObjectKey()
            );
        }

        return ImageMappingDto.builder()
                .imageType(defect.getImageType())
                .imageId(
                        defect.getInspectionImage() != null
                                ? defect.getInspectionImage().getId()
                                : null
                )
                .imgUrl(imageUrl)
                .volume(
                        defect.getInspectionImage() != null
                                ? defect.getInspectionImage().getVolume()
                                : null
                )
                .index(
                        defect.getInspectionImage() != null
                                ? defect.getInspectionImage().getIndex()
                                : null
                )
                .axis(
                        defect.getInspectionImage() != null
                                ? defect.getInspectionImage().getAxis()
                                : null
                )
                .bbox(defect.getBbox())
                .build();
    }
}
