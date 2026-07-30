package com.aivle.big_project.ai.llm.service;

import com.aivle.big_project.ai.llm.client.LlmWebClient;
import com.aivle.big_project.ai.llm.dto.*;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.report.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAsyncService {

    private final ReportsDailyRepository reportsDailyRepository;
    private final ReportsIndividualRepository reportsIndividualRepository;
    private final DefectResultRepository defectResultRepository;
    private final com.aivle.big_project.domain.inspection.InspectionRepository inspectionRepository;
    private final LlmWebClient llmWebClient;
    private final ObjectMapper objectMapper;

    // 최대 2개의 스레드만 동시 접근 허용
    private final Semaphore semaphore = new Semaphore(2);

    @Async
    @Transactional
    public void generateDailyReportAsync(Long reportId) {
        log.info("[Async] Starting daily report generation for ID: {}", reportId);

        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                log.warn("Semaphore full. Aborting daily report {} for now (will be retried by scheduler)", reportId);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            ReportsDaily report = reportsDailyRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일일 리포트입니다."));

            report.markAsDispatched();
            reportsDailyRepository.saveAndFlush(report);

            java.time.LocalDate targetDate = report.getReportDate();
            java.time.LocalDate prevDate = targetDate.minusDays(1);

            int totalCount = inspectionRepository.countTotalInspectedByDate(targetDate);
            int passCount = inspectionRepository.countByFinalLabelAndDate(targetDate, "PASS");
            int rejectCount = inspectionRepository.countByFinalLabelAndDate(targetDate, "REJECT");
            int failedCount = inspectionRepository.countByFinalLabelAndDate(targetDate, "FAIL");

            int prevTotalCount = inspectionRepository.countTotalInspectedByDate(prevDate);
            int prevRejectCount = inspectionRepository.countByFinalLabelAndDate(prevDate, "REJECT");

            List<Object[]> defectTypesRaw = defectResultRepository.findDefectResultType(targetDate, targetDate, 10);
            List<VlmDefectCount> defects = defectTypesRaw.stream()
                .map(row -> new VlmDefectCount(row[0].toString(), ((Number) row[1]).intValue()))
                .toList();

            VlmSummaryData summaryData = new VlmSummaryData(
                totalCount,
                passCount,
                rejectCount,
                failedCount,
                prevTotalCount,
                prevRejectCount,
                defects
            );

            try {
                report.updateSummaryJson(objectMapper.writeValueAsString(summaryData));
            } catch(Exception e) {
                log.warn("Failed to parse summaryJson for daily report {}", reportId);
            }

            VlmDailyReportRequest request = new VlmDailyReportRequest(
                    targetDate.toString(),
                    summaryData
            );

            try {
                VlmReportResponse response = llmWebClient.requestDailyReport(request, reportId).block();
                if (response != null) {
                    ReportStatus finalStatus = "COMPLETED".equalsIgnoreCase(response.status()) ? ReportStatus.COMPLETED : ReportStatus.FAILED;
                    report.updateResult(finalStatus, response.title(), response.content(), response.failureReason());
                } else {
                    report.updateResult(ReportStatus.FAILED, null, null, "EMPTY_RESPONSE");
                }
            } catch (Exception ex) {
                log.error("Error during daily report request to VLM", ex);
                report.updateResult(ReportStatus.FAILED, null, null, "AI_SERVER_ERROR");
            }
            
            reportsDailyRepository.save(report);

        } catch (Exception e) {
            log.error("[Async] Error during daily report generation", e);
        } finally {
            semaphore.release();
        }
    }

    @Async
    @Transactional
    public void generateIndividualReportAsync(Long reportId) {
        log.info("[Async] Starting individual report generation for ID: {}", reportId);

        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                log.warn("Semaphore full. Aborting individual report {} for now (will be retried by scheduler)", reportId);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            ReportsIndividual report = reportsIndividualRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 개별 리포트입니다."));

            report.markAsDispatched();
            reportsIndividualRepository.saveAndFlush(report);

            List<List<Double>> pointGroups = new ArrayList<>();
            if (report.getRepresentativeInspection() != null && report.getRepresentativeInspection().getPointGroups() != null) {
                try {
                    pointGroups = objectMapper.readValue(
                            report.getRepresentativeInspection().getPointGroups(),
                            new TypeReference<List<List<Double>>>() {}
                    );
                } catch (Exception e) {
                    log.warn("Failed to parse pointGroups for inspection {}", report.getRepresentativeInspection().getId());
                }
            }

            List<VlmImageDefectInfo> defectInfoList = new ArrayList<>();
            boolean hasCtData = false;
            int totalImages = 0;

            if (report.getRepresentativeInspection() != null) {
                List<DefectResult> defects = defectResultRepository.findByInspectionIdIn(List.of(report.getRepresentativeInspection().getId()));
                
                Map<Long, List<DefectResult>> defectsByImage = defects.stream()
                        .filter(d -> d.getInspectionImage() != null)
                        .collect(Collectors.groupingBy(d -> d.getInspectionImage().getId()));

                totalImages = defectsByImage.size();

                for (List<DefectResult> group : defectsByImage.values()) {
                    String imageType = group.get(0).getImageType();
                    if ("CT".equalsIgnoreCase(imageType)) hasCtData = true;
                    
                    List<String> defectTypes = group.stream()
                            .map(DefectResult::getDefectType)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();
                    
                    defectInfoList.add(new VlmImageDefectInfo(imageType, defectTypes));
                }
            }

            List<Double> cellSize = null;
            if (hasCtData && report.getBatteryCell().getCellSize() != null) {
                try {
                    cellSize = objectMapper.readValue(report.getBatteryCell().getCellSize(), new TypeReference<List<Double>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse cellSizeJson for cell {}", report.getBatteryCell().getId());
                }
            }

            VlmIndividualReportRequest request = new VlmIndividualReportRequest(
                    report.getBatteryCell().getCellSerialNo(),
                    report.getRepresentativeInspection() != null ? report.getRepresentativeInspection().getId() : null,
                    totalImages,
                    cellSize,
                    pointGroups,
                    null, // ctSeverity 임시값
                    null, // rgbSeverity 임시값
                    defectInfoList
            );

            try {
                VlmReportResponse response = llmWebClient.requestIndividualReport(request, reportId).block();
                if (response != null) {
                    ReportStatus finalStatus = "COMPLETED".equalsIgnoreCase(response.status()) ? ReportStatus.COMPLETED : ReportStatus.FAILED;
                    report.updateResult(finalStatus, response.title(), response.content(), response.failureReason());
                } else {
                    report.updateResult(ReportStatus.FAILED, null, null, "EMPTY_RESPONSE");
                }
            } catch (Exception ex) {
                log.error("Error during individual report request to VLM", ex);
                report.updateResult(ReportStatus.FAILED, null, null, "AI_SERVER_ERROR");
            }

            reportsIndividualRepository.save(report);

        } catch (Exception e) {
            log.error("[Async] Error during individual report generation", e);
        } finally {
            semaphore.release();
        }
    }
}
