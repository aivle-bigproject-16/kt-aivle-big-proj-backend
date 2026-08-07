package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.AiGatewayClient;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.CellProgress;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SimulationEvent;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.StartRequest;
import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import com.aivle.big_project.api.domain.simulation.client.config.AiGatewayProperties;
import com.aivle.big_project.domain.inspection.*;
import com.aivle.big_project.domain.simulation.SimulationRun;
import com.aivle.big_project.domain.simulation.SimulationRunRepository;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.simulation.SimulationStatus;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.api.domain.simulation.event.SimulationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private final SimulationRunRepository simulationRunRepository;
    private final BatteryCellRepository batteryCellRepository;
    private final InspectionBatchRepository inspectionBatchRepository;
    private final InspectionRepository inspectionRepository;
    private final SimulationSnapshotStore simulationSnapshotStore;
    private final SimulationEventPublisher simulationEventPublisher;
    private final InspectionImageRepository inspectionImageRepository;
    private final AiGatewayClient aiGatewayClient;
    private final AiGatewayProperties aiGatewayProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * POST /sim
     * 실행 중인 시뮬레이션이 없을 때 새 실행 이력을 생성합니다.
     */
    public SnapshotResponse start(StartRequest request) {
        boolean alreadyRunning = simulationRunRepository
                .findTopByStatusOrderByStartedAtDesc(SimulationStatus.RUNNING)
                .isPresent();

        if (alreadyRunning) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 실행 중인 시뮬레이션이 있습니다."
            );
        }

        Page<BatteryCell> batteryCellPage = batteryCellRepository.findAll(
                PageRequest.of(
                        0,
                        request.batteryCellCount(),
                        Sort.by("id").ascending()
                )
        );

        List<BatteryCell> batteryCells = batteryCellPage.getContent();

        if (batteryCells.size() < request.batteryCellCount()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검사할 배터리 셀 수가 부족합니다."
            );
        }

        SimulationRun simulationRun = SimulationRun.start(
                null, // 다음 인증 단계에서 로그인 사용자로 교체
                request.batchSize(),
                request.batteryCellCount(),
                request.captureSpeed()
        );

        simulationRunRepository.save(simulationRun);

        List<CellProgress> registered = new ArrayList<>();

        for (int startIndex = 0;
             startIndex < batteryCells.size();
             startIndex += request.batchSize()) {

            int endIndex = Math.min(
                    startIndex + request.batchSize(),
                    batteryCells.size()
            );

            InspectionBatch inspectionBatch = InspectionBatch.create(
                    simulationRun,
                    simulationRun.getRequestedBy()
            );

            inspectionBatchRepository.save(inspectionBatch);

            List<BatteryCell> cellsInBatch =
                    batteryCells.subList(startIndex, endIndex);

            for (BatteryCell batteryCell : cellsInBatch) {
                Inspection ctInspection = Inspection.create(
                        inspectionBatch,
                        batteryCell,
                        InspectionType.CT
                );
                Inspection rgbInspection = Inspection.create(
                        inspectionBatch,
                        batteryCell,
                        InspectionType.RGB
                );

                inspectionRepository.save(ctInspection);
                inspectionRepository.save(rgbInspection);

                if (startIndex == 0) {
                    registered.add(new CellProgress(
                            batteryCell.getId(),
                            ctInspection.getId(),
                            inspectionBatch.getId(),
                            ctInspection.getInspectionType(),
                            inspectionBatch.getStatus(),
                            null
                    ));
                    registered.add(new CellProgress(
                            batteryCell.getId(),
                            rgbInspection.getId(),
                            inspectionBatch.getId(),
                            rgbInspection.getInspectionType(),
                            inspectionBatch.getStatus(),
                            null
                    ));
                }
            }
        }

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                registered, // 첫 번째 배치 셀 목록
                List.of(), // 다음 단계: 촬영 중/완료 셀 목록 생성
                null,      // 다음 단계: 분석 중 셀 생성
                List.of()  // 다음 단계: 완료 셀 목록 생성
        );

        publishSnapshot(snapshot);

        applicationEventPublisher.publishEvent(
                new SimulationStartedEvent(
                        simulationRun.getId(),
                        simulationRun.getCaptureSpeed()
                )
        );

        return snapshot;
    }

    /**
     * 촬영시작
     * Registerd -> Capturing
     */
    public SnapshotResponse startBatchCapture(Long batchId) {
        InspectionBatch batch = inspectionBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "검사 배치를 찾을 수 없습니다."
                ));

        List<Inspection> inspections =
                inspectionRepository.findByInspectionBatchIdOrderByIdAsc(batch.getId());

        if (batch.getStatus() != InspectionBatchStatus.REGISTERED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "등록 상태의 배치만 촬영을 시작할 수 있습니다."
            );
        }
        batch.startCapture();

        inspections.forEach(Inspection::startCapture);

        List<CellProgress> capture = inspections.stream()
                .map(inspection -> new CellProgress(
                        inspection.getBatteryCell().getId(),
                        inspection.getId(),
                        batch.getId(),
                        inspection.getInspectionType(),
                        InspectionBatchStatus.CAPTURING,
                        inspection.getFinalLabel()
                ))
                .toList();

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                batch.getSimulationRun().getBatchCount(),
                batch.getSimulationRun().getBatteryCellCount(),
                batch.getSimulationRun().getCaptureSpeed(),
                List.of(),
                capture,
                null,
                List.of()
        );

        publishSnapshot(snapshot);

        return snapshot;
    }

    /**
     *
     * 촬영완료
     * Capturing -> Captured
     */
    public SnapshotResponse completeBatchCapture(Long batchId) {
        InspectionBatch batch = inspectionBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "검사 배치를 찾을 수 없습니다."
                ));

        if (batch.getStatus() != InspectionBatchStatus.CAPTURING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "촬영 중인 배치만 촬영 완료 처리할 수 있습니다."
            );
        }

        List<Inspection> inspections =
                inspectionRepository.findByInspectionBatchIdOrderByIdAsc(batchId);

        batch.completeCapture();
        inspections.forEach(Inspection::completeCapture);

        SimulationRun simulationRun = batch.getSimulationRun();

        List<CellProgress> capture = inspections.stream()
                .map(inspection -> new CellProgress(
                        inspection.getBatteryCell().getId(),
                        inspection.getId(),
                        batch.getId(),
                        inspection.getInspectionType(),
                        InspectionBatchStatus.CAPTURED,
                        inspection.getFinalLabel()
                ))
                .toList();

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                List.of(),
                capture,
                null,
                List.of()
        );

        publishSnapshot(snapshot);

        return snapshot;
    }

    /**
     *
     * 분석 시작
     * Captured -> Analyzing
     */
    public Optional<CellProgress> startNextAnalysis(Long simulationRunId) {
        boolean aiIsBusy =
                inspectionRepository
                        .existsByInspectionBatchSimulationRunIdAndStatus(
                                simulationRunId,
                                InspectionStatus.ANALYZING
                        );

        if (aiIsBusy) {
            return Optional.empty();
        }

        Optional<Inspection> nextInspectionOptional =
                inspectionRepository
                        .findFirstByInspectionBatchSimulationRunIdAndStatusOrderByIdAsc(
                                simulationRunId,
                                InspectionStatus.CAPTURED
                        );

        if (nextInspectionOptional.isEmpty()) {
            return Optional.empty();
        }

        Inspection inspection = nextInspectionOptional.get();
        InspectionBatch batch = inspection.getInspectionBatch();

        List<InspectionImage> images =
                inspectionImageRepository.findByInspectionIdIn(
                        List.of(inspection.getId())
                );

        if (images.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "분석할 이미지가 없습니다. inspectionId=%d"
                            .formatted(inspection.getId())
            );
        }

        String requestId = UUID.randomUUID().toString();

        AiServerDto.CellAnalysisRequest aiRequest =
                new AiServerDto.CellAnalysisRequest(
                        requestId,
                        batch.getId(),
                        inspection.getId(),
                        inspection.getBatteryCell().getId(),
                        inspection.getBatteryCell().getCellSerialNo(),
                        Instant.now(),
                        aiGatewayProperties.callbackUrl(),
                        images.stream()
                                .map(image -> new AiServerDto.ImageRequest(
                                        image.getId(),
                                        image.getImageType(),
                                        image.getBucketName(),
                                        image.getObjectKey()
                                ))
                                .toList()
                );

        inspection.startAnalysis(requestId);
        batch.startAnalysis();

        AiServerDto.AcceptedResponse accepted =
                aiGatewayClient.requestCellAnalysis(aiRequest);

        if (accepted == null
                || !accepted.accepted()
                || !requestId.equals(accepted.requestId())
                || !inspection.getId().equals(accepted.inspectionId())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 분석 요청 접수에 실패했습니다."
            );
        }

        CellProgress analyze = new CellProgress(
                inspection.getBatteryCell().getId(),
                inspection.getId(),
                batch.getId(),
                inspection.getInspectionType(),
                InspectionBatchStatus.ANALYZING,
                null
        );

        SimulationRun simulationRun = batch.getSimulationRun();

        SnapshotResponse current = simulationSnapshotStore.find()
                .orElse(null);

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                current == null ? List.of() : current.registered(),
                current == null ? List.of() : current.capture(),
                analyze,
                current == null ? List.of() : current.completed()
        );

        publishSnapshot(snapshot);

        return Optional.of(analyze);
    }

    /**
     * GET /sim
     * 현재 실행 중인 시뮬레이션의 기본 정보를 반환합니다.
     */
    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshot() {
        return simulationSnapshotStore.find()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "진행 중인 시뮬레이션이 없습니다."
                ));
    }

    /**
     * 상태 변경 전용 메서드
     * 상태 변경시마다 redis저장과 websocket 전송
     */
    private void publishSnapshot(SnapshotResponse snapshot) {
        simulationSnapshotStore.save(snapshot);
        simulationEventPublisher.publish(snapshot);
    }
}