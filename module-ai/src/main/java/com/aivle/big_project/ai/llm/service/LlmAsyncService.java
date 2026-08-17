package com.aivle.big_project.ai.llm.service;

import com.aivle.big_project.ai.llm.client.LlmWebClient;
import com.aivle.big_project.ai.llm.dto.*;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionStatus;
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
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.BitSet;
import java.awt.Rectangle;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAsyncService {

    private final ReportsDailyRepository reportsDailyRepository;
    private final ReportsIndividualRepository reportsIndividualRepository;
    private final DefectResultRepository defectResultRepository;
    private final com.aivle.big_project.domain.inspection.InspectionRepository inspectionRepository;
    private final InspectionImageRepository inspectionImageRepository;
    private final LlmWebClient llmWebClient;
    private final ObjectMapper objectMapper;

    // 최대 2개의 스레드만 동시 접근 허용
    private final Semaphore semaphore = new Semaphore(2);
    
    // 현재 JVM 메모리 큐(세마포어 대기 + 실행 중)에 있는 리포트 ID 추적
    private final Set<Long> inProgressDaily = ConcurrentHashMap.newKeySet();
    private final Set<Long> inProgressIndividual = ConcurrentHashMap.newKeySet();

    public boolean isDailyInProgress(Long id) {
        return inProgressDaily.contains(id);
    }

    public boolean isIndividualInProgress(Long id) {
        return inProgressIndividual.contains(id);
    }

    @Async
    public void generateDailyReportAsync(Long reportId) {
        // 큐에 진입함을 표시 (스케줄러가 건드리지 않도록)
        if (!inProgressDaily.add(reportId)) {
            log.info("Daily report {} is already in progress queue. Skipping duplicate request.", reportId);
            return;
        }

        try {
            // 세마포어 자리가 날 때까지 무한 대기 (즉시 취소되지 않고 큐처럼 동작)
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inProgressDaily.remove(reportId);
            return;
        }

        try {
            LocalDateTime dispatchedAt = LocalDateTime.now();
            int claimed = reportsDailyRepository.claimForGeneration(
                    reportId,
                    dispatchedAt,
                    dispatchedAt.minusMinutes(10)
            );
            if (claimed == 0) {
                log.info("Daily report {} was already claimed by another worker. Skipping duplicate request.", reportId);
                return;
            }

            ReportsDaily report = reportsDailyRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일일 리포트입니다."));

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

            VlmDailyData dailyData = new VlmDailyData(
                    targetDate.toString(),
                    summaryData
            );
            VlmDailyReportRequest request = new VlmDailyReportRequest(dailyData);

            VlmReportResponse response = null;
            String failureReason = null;
            try {
                response = llmWebClient.requestDailyReport(request, reportId).block();
                if (response == null) failureReason = "EMPTY_RESPONSE";
            } catch (Exception ex) {
                log.error("Error during daily report request to VLM", ex);
                failureReason = (ex.toString().contains("Timeout") || (ex.getMessage() != null && ex.getMessage().contains("Timeout")))
                        ? "AI_SERVER_TIMEOUT" : "AI_SERVER_ERROR";
            }

            ReportsDaily resultReport = reportsDailyRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일일 리포트입니다."));
            resultReport.updateSummaryJson(objectMapper.writeValueAsString(summaryData));
            if (response != null) {
                ReportStatus finalStatus = "COMPLETED".equalsIgnoreCase(response.status()) ? ReportStatus.COMPLETED : ReportStatus.FAILED;
                resultReport.updateResult(finalStatus, response.title(), response.content(), response.failureReason());
            } else {
                resultReport.updateResult(ReportStatus.FAILED, null, null, failureReason);
            }
            reportsDailyRepository.save(resultReport);

        } catch (Exception e) {
            log.error("[Async] Error during daily report generation", e);
            reportsDailyRepository.findById(reportId).ifPresent(report -> {
                report.updateResult(
                        ReportStatus.FAILED,
                        null,
                        null,
                        "WORKER_ERROR:" + e.getClass().getSimpleName()
                );
                reportsDailyRepository.save(report);
            });
        } finally {
            semaphore.release();
            inProgressDaily.remove(reportId);
        }
    }

    @Async
    @Transactional
    public void generateIndividualReportAsync(Long reportId) {
        // 큐에 진입함을 표시 (스케줄러가 건드리지 않도록)
        if (!inProgressIndividual.add(reportId)) {
            log.info("Individual report {} is already in progress queue. Skipping duplicate request.", reportId);
            return;
        }

        try {
            // 세마포어 자리가 날 때까지 무한 대기
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inProgressIndividual.remove(reportId);
            return;
        }

        try {
            ReportsIndividual report = reportsIndividualRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 개별 리포트입니다."));

            report.markAsDispatched();
            reportsIndividualRepository.saveAndFlush(report);

            List<Long> sourceInspectionIds = resolveSourceInspectionIds(report);
            List<Inspection> sourceInspections = inspectionRepository.findAllById(sourceInspectionIds);
            if (sourceInspections.isEmpty()) {
                throw new IllegalStateException("리포트에 연결된 검사가 없습니다.");
            }
            InspectionOutcome outcome = summarizeOutcome(sourceInspections);

            List<InspectionImage> inspectionImages =
                    inspectionImageRepository.findByInspectionIdIn(sourceInspectionIds);
            List<DefectResult> allResults =
                    defectResultRepository.findByInspectionIdIn(sourceInspectionIds);
            List<DefectResult> actualDefects = allResults.stream()
                    .filter(d -> d.getInspectionImage() != null)
                    .filter(d -> d.getDefectType() != null)
                    .filter(d -> d.getBbox() != null && !d.getBbox().isBlank())
                    .toList();

            List<List<Double>> pointGroups = readPointGroups(sourceInspections);

            List<VlmImageDefectInfo> defectInfoList = new ArrayList<>();
            boolean hasCtData = false;
            int totalImages = inspectionImages.size();

            double ctRatioSum = 0.0;
            double ctRatioMax = 0.0;
            int ctPoreCount = 0;
            int ctSliceCount = 0;

            long rgbDefectPixels = 0;
            long rgbTotalPixels = inspectionImages.stream()
                    .filter(i -> "RGB".equalsIgnoreCase(i.getImageType()))
                    .filter(i -> i.getWidth() != null && i.getHeight() != null)
                    .mapToLong(i -> (long) i.getWidth() * i.getHeight())
                    .sum();
            hasCtData = inspectionImages.stream()
                    .anyMatch(i -> "CT".equalsIgnoreCase(i.getImageType()));

            Map<Long, List<DefectResult>> defectsByImage = actualDefects.stream()
                    .collect(Collectors.groupingBy(d -> d.getInspectionImage().getId()));

            for (List<DefectResult> group : defectsByImage.values()) {
                    String imageType = group.get(0).getImageType();
                    InspectionImage img = group.get(0).getInspectionImage();
                    long unionArea = calculateUnionArea(group);

                    if ("CT".equalsIgnoreCase(imageType)) {
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
                    }
                    
                    List<String> defectTypes = group.stream()
                            .map(DefectResult::getDefectType)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    
                    defectInfoList.add(new VlmImageDefectInfo(imageType, defectTypes));
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
            if (hasCtData && (cellSize == null || cellSize.size() != 3)) {
                cellSize = List.of(100.0, 254.0, 871.0);
            }
            if (pointGroups.isEmpty() && cellSize != null) {
                pointGroups = buildPointGroups(actualDefects, cellSize);
            }

            VlmIndividualReportRequest request = new VlmIndividualReportRequest(
                    report.getBatteryCell().getCellSerialNo(),
                    report.getRepresentativeInspection() != null ? report.getRepresentativeInspection().getId() : null,
                    totalImages,
                    cellSize,
                    pointGroups,
                    ctSeverity,
                    rgbSeverity,
                    defectInfoList,
                    sourceInspectionIds,
                    outcome.finalLabel(),
                    outcome.status(),
                    outcome.failureType(),
                    outcome.failureReason()
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
                String reason = (ex.toString().contains("Timeout") || (ex.getMessage() != null && ex.getMessage().contains("Timeout")))
                        ? "AI_SERVER_TIMEOUT" : "AI_SERVER_ERROR";
                report.updateResult(ReportStatus.FAILED, null, null, reason);
            }

            reportsIndividualRepository.save(report);

        } catch (Exception e) {
            log.error("[Async] Error during individual report generation", e);
            reportsIndividualRepository.findById(reportId).ifPresent(report -> {
                report.updateResult(
                        ReportStatus.FAILED,
                        null,
                        null,
                        "WORKER_ERROR:" + e.getClass().getSimpleName()
                );
                reportsIndividualRepository.save(report);
            });
        } finally {
            semaphore.release();
            inProgressIndividual.remove(reportId);
        }
    }

    private List<Long> resolveSourceInspectionIds(ReportsIndividual report) {
        if (report.getSourceInspectionIds() != null) {
            try {
                List<Long> ids = objectMapper.readValue(
                        report.getSourceInspectionIds(),
                        new TypeReference<List<Long>>() {}
                );
                if (!ids.isEmpty()) {
                    return ids;
                }
            } catch (Exception e) {
                throw new IllegalStateException("리포트 검사 출처를 읽을 수 없습니다.", e);
            }
        }
        if (report.getRepresentativeInspection() != null) {
            return List.of(report.getRepresentativeInspection().getId());
        }
        return List.of();
    }

    private List<List<Double>> readPointGroups(List<Inspection> inspections) {
        List<List<Double>> result = new ArrayList<>();
        for (Inspection inspection : inspections) {
            if (inspection.getPointGroups() == null) {
                continue;
            }
            try {
                result.addAll(objectMapper.readValue(
                        inspection.getPointGroups(),
                        new TypeReference<List<List<Double>>>() {}
                ));
            } catch (Exception e) {
                log.warn("Failed to parse pointGroups for inspection {}", inspection.getId());
            }
        }
        return result;
    }

    private InspectionOutcome summarizeOutcome(List<Inspection> inspections) {
        Inspection failed = inspections.stream()
                .filter(i -> i.getStatus() == InspectionStatus.FAILED)
                .findFirst()
                .orElse(null);
        String finalLabel = failed != null
                ? "FAIL"
                : inspections.stream().anyMatch(i -> i.getFinalLabel() == FinalLabel.REJECT)
                        ? "REJECT" : "PASS";
        return new InspectionOutcome(
                finalLabel,
                failed != null ? "FAILED" : "COMPLETED",
                failed != null && failed.getFailureType() != null
                        ? failed.getFailureType().name() : null,
                failed != null ? failed.getFailureReason() : null
        );
    }

    private List<List<Double>> buildPointGroups(
            List<DefectResult> defects,
            List<Double> cellSize
    ) {
        List<List<Double>> points = new ArrayList<>();
        for (DefectResult defect : defects) {
            InspectionImage image = defect.getInspectionImage();
            if (!"CT".equalsIgnoreCase(defect.getImageType())
                    || image == null
                    || image.getAxis() == null
                    || image.getIndex() == null
                    || image.getVolume() == null
                    || image.getVolume() <= 0
                    || image.getWidth() == null
                    || image.getHeight() == null
                    || image.getWidth() <= 0
                    || image.getHeight() <= 0) {
                continue;
            }
            try {
                JsonNode bbox = objectMapper.readTree(defect.getBbox());
                double horizontal = clamp01(
                        (bbox.get("x").asDouble() + bbox.get("width").asDouble() / 2.0)
                                / image.getWidth()
                );
                double vertical = clamp01(
                        (bbox.get("y").asDouble() + bbox.get("height").asDouble() / 2.0)
                                / image.getHeight()
                );
                double slice = clamp01(
                        (image.getIndex() - 0.5) / (double) image.getVolume()
                );
                double a = cellSize.get(0);
                double b = cellSize.get(1);
                double c = cellSize.get(2);
                points.add(switch (image.getAxis().toLowerCase()) {
                    case "x" -> List.of(slice * a, horizontal * b, vertical * c);
                    case "y" -> List.of(horizontal * a, slice * b, vertical * c);
                    case "z" -> List.of(horizontal * a, vertical * b, slice * c);
                    default -> throw new IllegalArgumentException("unknown CT axis");
                });
            } catch (Exception e) {
                log.warn("Failed to build CT point for defect {}", defect.getId());
            }
        }
        return points;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record InspectionOutcome(
            String finalLabel,
            String status,
            String failureType,
            String failureReason
    ) {}

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
