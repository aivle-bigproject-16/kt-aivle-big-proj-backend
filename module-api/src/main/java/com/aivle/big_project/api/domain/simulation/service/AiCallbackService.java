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
import com.aivle.big_project.domain.simulation.SimulationRun;
import com.aivle.big_project.api.domain.simulation.event.InspectionAnalysisCompletedEvent;
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
    private final InspectionBatch inspectionBatch;

    public AiServerDto.CallbackResponse handle(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        Inspection inspection = inspectionRepository //ai request id로 inspection 조회
                .findByAiRequestId(callback.requestId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI 요청 정보를 찾을 수 없습니다."
                ));

        validateCallback(inspection, callback);

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

        InspectionStatus inspectionStatus =
                toInspectionStatus(callback.cellStatus());

        FinalLabel finalLabel = toFinalLabel(callback);

        List<DefectResult> defectResults =
                createDefectResults(inspection, callback);

        defectResultRepository.saveAll(defectResults);

        inspection.completeAnalysis(
                inspectionStatus,
                finalLabel,
                callback.failureReason()
        );

        completeBatchIfFinished(inspection.getInspectionBatch());

        SimulationRun simulationRun = inspection.getInspectionBatch()
                .getSimulationRun();

        boolean simulationCompleted =
                completeSimulationRunIfFinished(simulationRun);

        publishCompletedSnapshot(
                inspection,
                finalLabel,
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
                callback.imageResults().size(),
                false,
                "AI 셀 분석 결과를 저장했습니다."
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

    private InspectionStatus toInspectionStatus(String cellStatus) {
        return switch (cellStatus) {
            case "COMPLETED" -> InspectionStatus.COMPLETED;
            case "FAILED" -> InspectionStatus.FAILED;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 AI 셀 상태입니다: " + cellStatus
            );
        };
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
                        imageResult.latencyMs()
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
                        imageResult.latencyMs()
                ));
            }
        }

        return results;
    }

    private void publishCompletedSnapshot(
            Inspection inspection,
            FinalLabel finalLabel,
            boolean simulationCompleted
    ) {
        SnapshotResponse current = simulationSnapshotStore.find()
                .orElse(null);

        List<CellProgress> completed = new ArrayList<>();

        if (current != null) {
            completed.addAll(current.completed());

            completed.removeIf(progress ->
                    progress.inspectionId().equals(inspection.getId())
            );
        }

        completed.add(new CellProgress(
                inspection.getBatteryCell().getId(),
                inspection.getId(),
                inspection.getInspectionBatch().getId(),
                inspection.getInspectionType(),
                InspectionBatchStatus.COMPLETED,
                finalLabel
        ));

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
                current == null ? List.of() : current.capture(),
                null,
                completed
        );

        simulationSnapshotStore.save(snapshot);
        simulationEventPublisher.publish(snapshot);
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
}
