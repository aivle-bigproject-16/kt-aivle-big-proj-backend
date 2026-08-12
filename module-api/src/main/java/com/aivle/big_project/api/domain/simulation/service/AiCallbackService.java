package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.CellProgress;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SimulationEvent;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.*;
import com.aivle.big_project.domain.inspection.InspectionFailureType;
import com.aivle.big_project.domain.simulation.SimulationRun;
import com.aivle.big_project.api.domain.simulation.event.InspectionAnalysisCompletedEvent;
import com.aivle.big_project.api.domain.simulation.event.InspectionRecaptureRequestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AiCallbackService {

    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository inspectionImageRepository;
    private final DefectResultRepository defectResultRepository;
    private final SimulationSnapshotStore simulationSnapshotStore;
    private final SimulationEventPublisher simulationEventPublisher;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private static final int MAX_CAPTURE_RETRY_COUNT = 2;

    public AiServerDto.CallbackResponse handle(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        Inspection inspection = inspectionRepository
                .findByAiRequestId(callback.requestId())
                .orElse(null);

        if (inspection == null) {
            boolean duplicate =
                    defectResultRepository.existsByAiRequestId(
                            callback.requestId()
                    );

            if (duplicate) {
                return new AiServerDto.CallbackResponse(
                        true,
                        callback.requestId(),
                        callback.batchId(),
                        callback.batteryCellId(),
                        0,
                        true,
                        "이미 처리된 과거 AI 콜백입니다."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "AI 요청 정보를 찾을 수 없습니다."
            );
        }

        validateCallback(inspection, callback);
        validateFailureCallback(callback);
        validateFailureCallback(callback);

        if (isAlreadyProcessed(inspection)) {
            return new AiServerDto.CallbackResponse(
                    true,
                    callback.requestId(),
                    callback.batchId(),
                    callback.batteryCellId(),
                    0,
                    true,
                    "이미 처리된 AI 콜백입니다."
            );
        }

        boolean failed = "FAILED".equals(callback.cellStatus());

        InspectionFailureType failureType =
                toInspectionFailureType(callback);

        int savedResultCount;

        // 실패 콜백은 imageResults가 없어도 실패 이력 한 건을 저장
        if (failed) {
            defectResultRepository.save(
                    createFailureResult(inspection, callback)
            );

            savedResultCount = 1;
        } else {
            List<DefectResult> defectResults =
                    createDefectResults(inspection, callback);

            defectResultRepository.saveAll(defectResults);

            savedResultCount = defectResults.size();
        }

        SimulationRun simulationRun = inspection
                .getInspectionBatch()
                .getSimulationRun();

        // 촬영 실패이고 재촬영 횟수가 남아 있으면 최종 실패 처리하지 않음
        if (failed
                && failureType == InspectionFailureType.CAPTURE
                && inspection.canRetryCapture(MAX_CAPTURE_RETRY_COUNT)) {

            inspection.prepareRecapture(
                    failureType,
                    callback.failureReason()
            );

            publishRecaptureSnapshot(inspection);

            // 현재 Inspection 재촬영 예약
            applicationEventPublisher.publishEvent(
                    new InspectionRecaptureRequestedEvent(
                            simulationRun.getId(),
                            inspection.getId(),
                            simulationRun.getCaptureSpeed()
                    )
            );

            // AI 서버는 Run 전체의 다음 CAPTURED Inspection 처리
            applicationEventPublisher.publishEvent(
                    new InspectionAnalysisCompletedEvent(
                            simulationRun.getId()
                    )
            );

            return new AiServerDto.CallbackResponse(
                    true,
                    callback.requestId(),
                    callback.batchId(),
                    callback.batteryCellId(),
                    savedResultCount,
                    false,
                    "촬영 실패 이력을 저장하고 재촬영을 예약했습니다."
            );
        }

        // 여기부터는 성공 또는 최종 실패 처리
        InspectionStatus finalStatus = failed
                ? InspectionStatus.FAILED
                : InspectionStatus.COMPLETED;

        FinalLabel finalLabel = failed
                ? FinalLabel.FAIL
                : toFinalLabel(callback);

        inspection.completeAnalysis(
                finalStatus,
                finalLabel,
                failed ? failureType : null,
                failed ? callback.failureReason() : null
        );

        completeBatchIfFinished(
                inspection.getInspectionBatch()
        );

        boolean simulationCompleted =
                completeSimulationRunIfFinished(simulationRun);

        Optional<CellProgress> completedCell =
                completeCellIfFinished(inspection);

        publishCompletedSnapshot(
                inspection,
                completedCell,
                simulationCompleted
        );

        if (!simulationCompleted) {
            applicationEventPublisher.publishEvent(
                    new InspectionAnalysisCompletedEvent(
                            simulationRun.getId()
                    )
            );
        }

        return new AiServerDto.CallbackResponse(
                true,
                callback.requestId(),
                callback.batchId(),
                callback.batteryCellId(),
                savedResultCount,
                false,
                failed
                        ? "AI 셀 분석 실패 결과를 저장했습니다."
                        : "AI 셀 분석 결과를 저장했습니다."
        );
    }

    //메서드
    private boolean completeSimulationRunIfFinished(
            SimulationRun simulationRun
    ) {
        boolean hasUnfinishedInspection =
                inspectionRepository
                        .existsByInspectionBatchSimulationRunIdAndStatusIn(
                                simulationRun.getId(),
                                List.of(
                                        InspectionStatus.PENDING,
                                        InspectionStatus.CAPTURING,
                                        InspectionStatus.CAPTURED,
                                        InspectionStatus.ANALYZING
                                )
                        );

        if (hasUnfinishedInspection) {
            return false;
        }

        simulationRun.complete();

        return true;
    }

    private void validateCallback(
            Inspection inspection,
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        if (!inspection.getId().equals(callback.inspectionId())
                || !inspection.getBatteryCell().getId()
                .equals(callback.batteryCellId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI 콜백의 검사 또는 배터리 셀 정보가 일치하지 않습니다."
            );
        }
    }

    private boolean isAlreadyProcessed(Inspection inspection) {
        return inspection.getStatus() == InspectionStatus.COMPLETED
                || inspection.getStatus() == InspectionStatus.FAILED;
    }

    private FinalLabel toFinalLabel(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        if ("FAILED".equals(callback.cellStatus())) {
            return FinalLabel.FAIL;
        }

        try {
            return FinalLabel.valueOf(callback.finalLabel());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "유효하지 않은 최종 판정입니다."
            );
        }
    }

    private List<DefectResult> createDefectResults(
            Inspection inspection,
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        List<DefectResult> results = new ArrayList<>();

        for (AiServerDto.ImageAnalysisResult imageResult
                : callback.imageResults()) {

            InspectionImage image = inspectionImageRepository
                    .findById(imageResult.imageId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "검사 이미지를 찾을 수 없습니다."
                    ));

            if (!image.getInspection().getId()
                    .equals(inspection.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "이미지가 해당 검사에 속하지 않습니다."
                );
            }

            String rawResponse = imageResult.rawResponse() == null
                    ? null
                    : imageResult.rawResponse().toString();

            if (imageResult.defects() == null
                    || imageResult.defects().isEmpty()) {

                results.add(DefectResult.create(
                        inspection,
                        image,
                        imageResult.imageType(),
                        imageResult.label(),
                        null,
                        imageResult.confidence(),
                        null,
                        rawResponse,
                        imageResult.latencyMs(),
                        inspection.currentAttemptNo(),
                        callback.requestId()
                ));

                continue;
            }

            for (AiServerDto.Defect defect : imageResult.defects()) {
                results.add(DefectResult.create(
                        inspection,
                        image,
                        imageResult.imageType(),
                        imageResult.label(),
                        defect.defectType(),
                        defect.confidence(),
                        toJson(defect.bbox()),
                        rawResponse,
                        imageResult.latencyMs(),
                        inspection.currentAttemptNo(),
                        callback.requestId()
                ));
            }
        }

        return results;
    }

    private void publishCompletedSnapshot(
            Inspection inspection,
            Optional<CellProgress> completedCell,
            boolean simulationCompleted
    ) {
        SnapshotResponse current = simulationSnapshotStore.find()
                .orElse(null);

        List<CellProgress> capture = current == null
                ? new ArrayList<>()
                : new ArrayList<>(current.capture());

        completedCell.ifPresent(cell ->
                capture.removeIf(progress ->
                        progress.batteryCellId().equals(cell.batteryCellId())
                                && progress.batchId().equals(cell.batchId())
                )
        );

        List<CellProgress> completed = new ArrayList<>();

        if (current != null) {
            completed.addAll(current.completed());
        }

        completedCell.ifPresent(cell -> {
            completed.removeIf(progress ->
                    progress.batteryCellId().equals(cell.batteryCellId())
                            && progress.batchId().equals(cell.batchId())
            );

            completed.add(cell);
        });

        SnapshotResponse snapshot = new SnapshotResponse(
                simulationCompleted
                        ? SimulationEvent.COMPLETED
                        : SimulationEvent.PROGRESS,
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getBatchCount(),
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getBatteryCellCount(),
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getCaptureSpeed(),
                current == null ? List.of() : current.registered(),
                capture,
                null,
                completed
        );

        simulationSnapshotStore.save(snapshot);
        simulationEventPublisher.publish(snapshot);
    }

    private Optional<CellProgress> completeCellIfFinished(
            Inspection completedInspection
    ) {
        List<Inspection> inspections =
                inspectionRepository
                        .findByInspectionBatchIdAndBatteryCellIdOrderByIdAsc(
                                completedInspection.getInspectionBatch().getId(),
                                completedInspection.getBatteryCell().getId()
                        );

        boolean hasUnfinishedInspection = inspections.stream()
                .anyMatch(inspection ->
                        inspection.getStatus() != InspectionStatus.COMPLETED
                                && inspection.getStatus() != InspectionStatus.FAILED
                );

        if (hasUnfinishedInspection) {
            return Optional.empty();
        }

        FinalLabel cellFinalLabel = resolveCellFinalLabel(inspections);

        return Optional.of(new CellProgress(
                completedInspection.getBatteryCell().getId(),
                completedInspection.getInspectionBatch().getId(),
                InspectionBatchStatus.COMPLETED,
                cellFinalLabel
        ));
    }

    private FinalLabel resolveCellFinalLabel(
            List<Inspection> inspections
    ) {
        boolean hasFail = inspections.stream()
                .anyMatch(inspection ->
                        inspection.getFinalLabel() == FinalLabel.FAIL
                );

        if (hasFail) {
            return FinalLabel.FAIL;
        }

        boolean hasReject = inspections.stream()
                .anyMatch(inspection ->
                        inspection.getFinalLabel() == FinalLabel.REJECT
                );

        if (hasReject) {
            return FinalLabel.REJECT;
        }

        return FinalLabel.PASS;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "AI 결함 좌표 JSON 변환에 실패했습니다.",
                    exception
            );
        }
    }

    private void completeBatchIfFinished(
            InspectionBatch batch
    ) {
        boolean hasUnfinishedInspection =
                inspectionRepository.existsByInspectionBatchIdAndStatusIn(
                        batch.getId(),
                        List.of(
                                InspectionStatus.PENDING,
                                InspectionStatus.CAPTURING,
                                InspectionStatus.CAPTURED,
                                InspectionStatus.ANALYZING
                        )
                );

        if (!hasUnfinishedInspection) {
            batch.complete();
        }
    }

    private InspectionFailureType toInspectionFailureType(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        // 분석 성공 시에는 실패 유형이 없음
        if (!"FAILED".equals(callback.cellStatus())) {
            return null;
        }

        if (callback.failureType() == null
                || callback.failureType().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FAILED 콜백에는 failureType이 필요합니다."
            );
        }

        try {
            return InspectionFailureType.valueOf(
                    callback.failureType().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 failureType입니다: "
                            + callback.failureType()
            );
        }
    }

    private DefectResult createFailureResult(
            Inspection inspection,
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        return DefectResult.create(
                inspection,
                null,
                inspection.getInspectionType().name(),
                "FAIL",
                callback.failureReason(),
                callback.confidence(),
                null,
                toJson(callback),
                null,
                inspection.currentAttemptNo(),
                callback.requestId()
        );
    }

    private void validateFailureCallback(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        if (!"FAILED".equals(callback.cellStatus())) {
            return;
        }

        if (callback.failureType() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FAILED 콜백에는 failureType이 필요합니다."
            );
        }

        if (callback.failureReason() == null
                || callback.failureReason().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FAILED 콜백에는 failureReason이 필요합니다."
            );
        }

        if (callback.finalLabel() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FAILED 콜백의 finalLabel은 null이어야 합니다."
            );
        }
    }

    private void validateCallbackStatus(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        if (!"COMPLETED".equals(callback.cellStatus())
                && !"FAILED".equals(callback.cellStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 AI 셀 상태입니다: "
                            + callback.cellStatus()
            );
        }

        if ("COMPLETED".equals(callback.cellStatus())) {
            if (callback.finalLabel() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "COMPLETED 콜백에는 finalLabel이 필요합니다."
                );
            }

            if (!"PASS".equals(callback.finalLabel())
                    && !"REJECT".equals(callback.finalLabel())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "COMPLETED 콜백의 finalLabel은 PASS 또는 REJECT여야 합니다."
                );
            }

            if (callback.imageResults() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "COMPLETED 콜백에는 imageResults가 필요합니다."
                );
            }

            if (callback.failureType() != null
                    || callback.failureReason() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "COMPLETED 콜백에는 실패 정보가 없어야 합니다."
                );
            }
        }
    }

    private void publishRecaptureSnapshot(
            Inspection inspection
    ) {
        SnapshotResponse current = simulationSnapshotStore.find()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "시뮬레이션 스냅샷이 없습니다."
                ));

        List<CellProgress> capture =
                new ArrayList<>(current.capture());

        capture.removeIf(progress ->
                progress.batteryCellId().equals(
                        inspection.getBatteryCell().getId()
                )
                        && progress.batchId().equals(
                        inspection.getInspectionBatch().getId()
                )
        );

        capture.add(new CellProgress(
                inspection.getBatteryCell().getId(),
                inspection.getInspectionBatch().getId(),
                InspectionBatchStatus.CAPTURING,
                null
        ));

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getBatchCount(),
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getBatteryCellCount(),
                inspection.getInspectionBatch()
                        .getSimulationRun()
                        .getCaptureSpeed(),
                current.registered(),
                capture,
                null,
                current.completed()
        );

        simulationSnapshotStore.save(snapshot);
        simulationEventPublisher.publish(snapshot);
    }
}
