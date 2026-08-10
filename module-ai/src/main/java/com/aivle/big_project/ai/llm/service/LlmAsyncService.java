package com.aivle.big_project.ai.llm.service;

import com.aivle.big_project.ai.llm.client.LlmWebClient;
import com.aivle.big_project.ai.llm.dto.*;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.BitSet;
import java.awt.Rectangle;
import com.aivle.big_project.domain.image.InspectionImage;
import com.fasterxml.jackson.databind.JsonNode;

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
//        log.info("[Async] Starting daily report generation for ID: {}", reportId);

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

            LocalDate targetDate = report.getReportDate();
            LocalDate prevDate = targetDate.minusDays(1);

            int totalCount = inspectionRepository.countTotalInspectedByDate(targetDate);
            int passCount = inspectionRepository.countByFinalLabelAndDate(targetDate, FinalLabel.PASS);
            int rejectCount = inspectionRepository.countByFinalLabelAndDate(targetDate, FinalLabel.REJECT);
            int failedCount = inspectionRepository.countByFinalLabelAndDate(targetDate, FinalLabel.FAIL);

            int prevTotalCount = inspectionRepository.countTotalInspectedByDate(prevDate);
            int prevRejectCount = inspectionRepository.countByFinalLabelAndDate(prevDate, FinalLabel.REJECT);

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

            VlmDailyData dailyData = new VlmDailyData(
                    targetDate.toString(),
                    summaryData
            );
            VlmDailyReportRequest request = new VlmDailyReportRequest(dailyData);

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
//        log.info("[Async] Starting individual report generation for ID: {}", reportId);

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

            double ctRatioSum = 0.0;
            double ctRatioMax = 0.0;
            int ctPoreCount = 0;
            int ctSliceCount = 0;

            long rgbDefectPixels = 0;
            long rgbTotalPixels = 0;

            if (report.getRepresentativeInspection() != null) {
                List<DefectResult> defects = defectResultRepository.findByInspectionIdIn(List.of(report.getRepresentativeInspection().getId()));
                
                Map<Long, List<DefectResult>> defectsByImage = defects.stream()
                        .filter(d -> d.getInspectionImage() != null)
                        .collect(Collectors.groupingBy(d -> d.getInspectionImage().getId()));

                totalImages = defectsByImage.size();

                for (List<DefectResult> group : defectsByImage.values()) {
                    String imageType = group.get(0).getImageType();
                    InspectionImage img = group.get(0).getInspectionImage();
                    long unionArea = calculateUnionArea(group);

                    if ("CT".equalsIgnoreCase(imageType)) {
                        hasCtData = true;
                        ctSliceCount++;
                        ctPoreCount += group.size();
                        if (img.getWidth() != null && img.getHeight() != null) {
                            long totalPixels = (long) img.getWidth() * img.getHeight();
                            if (totalPixels > 0) {
                                double ratio = (double) unionArea / totalPixels;
                                ctRatioSum += ratio;
                                if (ratio > ctRatioMax) {
                                    ctRatioMax = ratio;
                                }
                            }
                        }
                    } else if ("RGB".equalsIgnoreCase(imageType)) {
                        rgbDefectPixels += unionArea;
                        if (img.getWidth() != null && img.getHeight() != null) {
                            rgbTotalPixels += (long) img.getWidth() * img.getHeight();
                        }
                    }
                    
                    List<String> defectTypes = group.stream()
                            .map(DefectResult::getDefectType)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    
                    defectInfoList.add(new VlmImageDefectInfo(imageType, defectTypes));
                }
            }

            Double ctSeverity = null;
            if (ctSliceCount > 0) {
                ctSeverity = ctRatioSum / ctSliceCount;
            }

            Double rgbSeverity = null;
            if (rgbTotalPixels > 0) {
                rgbSeverity = (double) rgbDefectPixels / rgbTotalPixels;
            }

            // Update Inspection entity
            if (report.getRepresentativeInspection() != null) {
                String ctMeanStr = ctSeverity != null ? String.format("%.7f", ctSeverity) : null;
                String ctMaxStr = String.format("%.7f", ctRatioMax);
                String ctPoreCountStr = String.valueOf(ctPoreCount);
                String ctSliceCountStr = String.valueOf(ctSliceCount);
                String rgbRatioStr = rgbSeverity != null ? String.format("%.7f", rgbSeverity) : null;

                report.getRepresentativeInspection().updateSeverity(
                        ctMeanStr, ctMaxStr, ctPoreCountStr, ctSliceCountStr, rgbRatioStr
                );
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
                    ctSeverity,
                    rgbSeverity,
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

    private long calculateUnionArea(List<DefectResult> defects) {
        if (defects == null || defects.isEmpty()) return 0;
        List<Rectangle> rects = new ArrayList<>();
        for (DefectResult defect : defects) {
            String bboxJson = defect.getBbox();
            if (bboxJson != null && !bboxJson.trim().isEmpty()) {
                try {
                    JsonNode node = objectMapper.readTree(bboxJson);
                    if (node.has("x") && node.has("y") && node.has("width") && node.has("height")) {
                        rects.add(new Rectangle(
                            node.get("x").asInt(), node.get("y").asInt(),
                            node.get("width").asInt(), node.get("height").asInt()
                        ));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse bbox: {}", bboxJson);
                }
            }
        }
        if (rects.isEmpty()) return 0;

        int maxX = 0;
        int maxY = 0;
        for (Rectangle r : rects) {
            maxX = Math.max(maxX, r.x + r.width);
            maxY = Math.max(maxY, r.y + r.height);
        }
        if (maxX <= 0 || maxY <= 0) return 0;

        BitSet[] grid = new BitSet[maxY];
        for (int i = 0; i < maxY; i++) {
            grid[i] = new BitSet(maxX);
        }
        for (Rectangle r : rects) {
            int endY = Math.min(maxY, r.y + r.height);
            int endX = Math.min(maxX, r.x + r.width);
            for (int y = Math.max(0, r.y); y < endY; y++) {
                grid[y].set(Math.max(0, r.x), endX);
            }
        }
        long area = 0;
        for (int i = 0; i < maxY; i++) {
            area += grid[i].cardinality();
        }
        return area;
    }
}
