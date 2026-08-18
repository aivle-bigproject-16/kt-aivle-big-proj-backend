package com.aivle.big_project.api.domain.report.service;

import com.aivle.big_project.api.domain.report.dto.*;
import com.aivle.big_project.api.global.response.PagedResponse;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionStatus;
import com.aivle.big_project.domain.inspection.InspectionType;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.report.*;
import com.aivle.big_project.api.global.storage.S3ImageUrlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;

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
    private final ObjectMapper objectMapper;

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

        Inspection representativeInspection =
                report.getRepresentativeInspection();

        if (representativeInspection == null) {
            return IndividualReportDetailResponse.of(
                    report,
                    List.of(),
                    null,
                    null,
                    null,
                    null
            );
        }

        /*
         * main의 기능:
         * 리포트에 포함해야 할 같은 셀·같은 배치의
         * CT/RGB Inspection을 모두 결정합니다.
         */
        List<Inspection> sourceInspections =
                resolveSourceInspections(report);

        List<Long> sourceInspectionIds = sourceInspections.stream()
                .map(Inspection::getId)
                .toList();

        List<ImageMappingDto> imageMappings = new ArrayList<>();

        if (!sourceInspectionIds.isEmpty()) {
            List<DefectResult> defects =
                    defectResultRepository.findByInspectionIdIn(
                            sourceInspectionIds
                    );

            // CT 결함 이미지 최대 10건
            List<ImageMappingDto> ctMappings = defects.stream()
                    .filter(defect ->
                            "CT".equalsIgnoreCase(defect.getImageType())
                                    && defect.getBbox() != null
                                    && defect.getInspectionImage() != null
                    )
                    .limit(10)
                    .map(this::toImageMapping)
                    .toList();

            // RGB 결함 이미지 최대 10건
            List<ImageMappingDto> rgbMappings = defects.stream()
                    .filter(defect ->
                            "RGB".equalsIgnoreCase(defect.getImageType())
                                    && defect.getBbox() != null
                                    && defect.getInspectionImage() != null
                    )
                    .limit(10)
                    .map(this::toImageMapping)
                    .toList();

            imageMappings.addAll(ctMappings);
            imageMappings.addAll(rgbMappings);
        }

        /*
         * main의 기능:
         * 같은 셀·같은 배치의 CT/RGB 결과를
         * FAIL → REJECT → PASS 순서로 통합합니다.
         */
        InspectionOutcome outcome =
                summarizeOutcome(sourceInspections);

        return IndividualReportDetailResponse.of(
                report,
                imageMappings,
                outcome.finalLabel(),
                outcome.status(),
                outcome.failureType(),
                outcome.failureReason()
        );
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

        List<Inspection> inspections = inspectionRepository
                .findAllByBatteryCellIdOrderByBatchDesc(cell.getId());
        if (inspections.isEmpty()) {
            throw new IllegalStateException("리포트를 생성할 검사 결과가 없습니다.");
        }
        Long latestBatchId = inspections.get(0).getInspectionBatch().getId();
        List<Inspection> sourceInspections = inspections.stream()
                .filter(i -> Objects.equals(i.getInspectionBatch().getId(), latestBatchId))
                .toList();
        boolean terminal = sourceInspections.stream().allMatch(i ->
                i.getStatus() == InspectionStatus.COMPLETED
                        || i.getStatus() == InspectionStatus.FAILED
        );
        if (!terminal) {
            throw new IllegalStateException("최신 CT/RGB 검사가 아직 완료되지 않았습니다.");
        }
        Inspection representativeInspection = sourceInspections.stream()
                .filter(i -> i.getInspectionType() == InspectionType.CT)
                .findFirst()
                .orElse(sourceInspections.get(0));
        String sourceInspectionIds;
        try {
            sourceInspectionIds = objectMapper.writeValueAsString(
                    sourceInspections.stream().map(Inspection::getId).toList()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("검사 출처를 직렬화할 수 없습니다.", e);
        }

        Integer maxVersion = reportsIndividualRepository.findMaxVersionByBatteryCellId(cell.getId());
        int nextVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        ReportsIndividual newReport = ReportsIndividual.builder()
                .batteryCell(cell)
                .version(nextVersion)
                .representativeInspection(representativeInspection)
                .sourceInspectionIds(sourceInspectionIds)
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

    private List<Inspection> resolveSourceInspections(ReportsIndividual report) {
        List<Long> ids = new ArrayList<>();
        if (report.getSourceInspectionIds() != null) {
            try {
                ids.addAll(objectMapper.readValue(
                        report.getSourceInspectionIds(),
                        new TypeReference<List<Long>>() {}
                ));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("리포트 검사 출처를 읽을 수 없습니다.", e);
            }
        }
        if (ids.isEmpty() && report.getRepresentativeInspection() != null) {
            ids.add(report.getRepresentativeInspection().getId());
        }
        return inspectionRepository.findAllById(ids);
    }

    private InspectionOutcome summarizeOutcome(List<Inspection> inspections) {
        Inspection failed = inspections.stream()
                .filter(i -> i.getStatus() == InspectionStatus.FAILED)
                .findFirst()
                .orElse(null);
        String finalLabel = inspections.stream()
                .anyMatch(i -> i.getStatus() == InspectionStatus.FAILED)
                ? "FAIL"
                : inspections.stream().anyMatch(i -> i.getFinalLabel() != null
                        && "REJECT".equals(i.getFinalLabel().name()))
                        ? "REJECT"
                        : "PASS";
        String status = failed != null ? "FAILED" : "COMPLETED";
        return new InspectionOutcome(
                finalLabel,
                status,
                failed != null && failed.getFailureType() != null
                        ? failed.getFailureType().name() : null,
                failed != null ? failed.getFailureReason() : null
        );
    }

    private record InspectionOutcome(
            String finalLabel,
            String status,
            String failureType,
            String failureReason
    ) {}

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
        var inspectionImage = defect.getInspectionImage();

        String imageUrl = s3ImageUrlService.createGetUrl(
                inspectionImage.getBucketName(),
                inspectionImage.getObjectKey()
        );

        return ImageMappingDto.builder()
                .imageType(defect.getImageType())
                .imageId(inspectionImage.getId())
                .imgUrl(imageUrl)
                .volume(inspectionImage.getVolume())
                .index(inspectionImage.getIndex())
                .axis(inspectionImage.getAxis())
                .imageWidth(inspectionImage.getWidth())
                .imageHeight(inspectionImage.getHeight())
                .bbox(defect.getBbox())
                .build();
    }

    @Transactional
    public void deleteDailyReport(Long id) {
        if (!reportsDailyRepository.existsById(id)) {
            throw new IllegalArgumentException("존재하지 않는 일일 리포트입니다.");
        }
        reportsDailyItemRepository.deleteByReportsDailyId(id);
        reportsDailyRepository.deleteById(id);
    }

    @Transactional
    public void deleteIndividualReport(Long id) {
        if (!reportsIndividualRepository.existsById(id)) {
            throw new IllegalArgumentException("존재하지 않는 개별 리포트입니다.");
        }
        reportsIndividualRepository.deleteById(id);
    }
}
