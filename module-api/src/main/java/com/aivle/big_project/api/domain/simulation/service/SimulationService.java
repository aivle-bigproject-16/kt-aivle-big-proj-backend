package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.AiGatewayClient;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.AnalysisProgress;
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
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private static final String SIMULATION_CELL_PREFIX = "SIM-";

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
    private final SimulationDataResetter simulationDataResetter;

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

        if (request.resetBeforeStart()) {
            simulationDataResetter.reset();
        }

        List<BatteryCell> batteryCells = batteryCellRepository
                .findByCellSerialNoStartingWithOrderByCellSerialNoAsc(
                        SIMULATION_CELL_PREFIX,
                        PageRequest.of(0, request.batteryCellCount())
                );

        if (batteryCells.size() < request.batteryCellCount()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SIM 시뮬레이션 셀 수가 부족합니다."
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

                registered.add(new CellProgress(
                        batteryCell.getId(),
                        inspectionBatch.getId(),
                        InspectionBatchStatus.REGISTERED,
                        null
                ));
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



        SimulationRun simulationRun = batch.getSimulationRun();

        // 이전 AI 분석·완료 정보가 지워지지 않게 현재 스냅샷을 유지
        SnapshotResponse current = simulationSnapshotStore.find()
                .orElseThrow();

        List<CellProgress> batchCapture = toCellProgresses(
                inspections,
                InspectionBatchStatus.CAPTURING
        );


        List<CellProgress> capture = replaceCaptureBatch(
                current.capture(),
                batchCapture
        );

        List<CellProgress> registered = removeRegisteredBatch(
                current.registered(),
                batchId
        );


        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                registered,
                capture,
                current.analyze(),
                current.completed()
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

        //프론트에는 cell 단위로 전달
        List<CellProgress> batchCapture = toCellProgresses(
                inspections,
                InspectionBatchStatus.CAPTURED
        );

        // 이전 AI 분석·완료 정보가 지워지지 않게 현재 스냅샷을 유지
        SnapshotResponse current = simulationSnapshotStore.find()
                .orElseThrow();

        List<CellProgress> capture = replaceCaptureBatch(
                current.capture(),
                batchCapture
        );

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                current.registered(),
                capture,
                current.analyze(),
                current.completed()
        );

        publishSnapshot(snapshot);

        return snapshot;
    }

    /**
     *
     * 분석 시작
     * Captured -> Analyzing
     */
    public Optional<AnalysisProgress> startNextAnalysis(Long simulationRunId) {
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

        int attemptNo = inspection.currentAttemptNo();

        List<InspectionImage> images =
                inspectionImageRepository
                        .findByInspectionIdAndAttemptNoOrderByIdAsc(
                                inspection.getId(),
                                attemptNo
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

        AnalysisProgress analyze = new AnalysisProgress(
                inspection.getBatteryCell().getId(),
                inspection.getId(),
                batch.getId(),
                inspection.getInspectionType(),
                InspectionStatus.ANALYZING,
                null
        );

        SimulationRun simulationRun = batch.getSimulationRun();

        SnapshotResponse current = simulationSnapshotStore.find()
                .orElseThrow();

        List<CellProgress> capture = removeCaptureCell(
                current.capture(),
                inspection.getBatteryCell().getId(),
                batch.getId()
        );

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                current.registered(),
                capture,
                analyze,
                current.completed()
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

    @Transactional
    public SnapshotResponse completeInspectionRecapture(
            Long inspectionId
    ) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "재촬영할 검사를 찾을 수 없습니다."
                ));

        if (inspection.getStatus() != InspectionStatus.CAPTURING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CAPTURING 상태의 검사만 촬영 완료할 수 있습니다."
            );
        }

        // DB 상태: CAPTURING → CAPTURED
        inspection.completeCapture();

        SnapshotResponse current = simulationSnapshotStore.find()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "진행 중인 시뮬레이션 스냅샷이 없습니다."
                ));

        List<CellProgress> capture =
                new ArrayList<>(current.capture());

        Long batteryCellId =
                inspection.getBatteryCell().getId();

        Long batchId =
                inspection.getInspectionBatch().getId();

        // 기존 CAPTURING 표시 제거
        capture.removeIf(progress ->
                progress.batteryCellId().equals(batteryCellId)
                        && progress.batchId().equals(batchId)
        );

        // CAPTURED 상태로 다시 추가
        capture.add(new CellProgress(
                batteryCellId,
                batchId,
                InspectionBatchStatus.CAPTURED,
                null
        ));

        SimulationRun simulationRun =
                inspection.getInspectionBatch().getSimulationRun();

        SnapshotResponse snapshot = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                simulationRun.getBatchCount(),
                simulationRun.getBatteryCellCount(),
                simulationRun.getCaptureSpeed(),
                current.registered(),
                capture,
                null,
                current.completed()
        );

        publishSnapshot(snapshot);

        return snapshot;
    }

    /**
     * 상태 변경 전용 메서드
     * 상태 변경시마다 redis저장과 websocket 전송
     */
    private void publishSnapshot(SnapshotResponse snapshot) {
        simulationSnapshotStore.save(snapshot);
        simulationEventPublisher.publish(snapshot);
    }

    /**
     * cell 단위 변환 보조 메서드
     * cell 단위로 진행되는 capture를 inspection단위로 변경
     */
    private List<CellProgress> toCellProgresses(
            List<Inspection> inspections,
            InspectionBatchStatus status
    ) {
        Map<Long, Inspection> uniqueCells = new LinkedHashMap<>();

        for (Inspection inspection : inspections) {
            uniqueCells.putIfAbsent(
                    inspection.getBatteryCell().getId(),
                    inspection
            );
        }

        return uniqueCells.values()
                .stream()
                .map(inspection -> new CellProgress(
                        inspection.getBatteryCell().getId(),
                        inspection.getInspectionBatch().getId(),
                        status,
                        inspection.getFinalLabel()
                ))
                .toList();
    }

    private List<CellProgress> removeRegisteredBatch(
            List<CellProgress> registered,
            Long batchId
    ) {
        return registered.stream()
                .filter(progress -> !progress.batchId().equals(batchId))
                .toList();
    }

    private List<CellProgress> replaceCaptureBatch(
            List<CellProgress> currentCapture,
            List<CellProgress> changedBatchCells
    ) {
        if (changedBatchCells.isEmpty()) {
            return currentCapture;
        }

        Long batchId = changedBatchCells.get(0).batchId();

        List<CellProgress> result = new ArrayList<>(currentCapture);

        // 같은 배치의 이전 상태만 제거
        result.removeIf(progress -> progress.batchId().equals(batchId));

        // CAPTURING 또는 CAPTURED로 바뀐 해당 배치 셀 추가
        result.addAll(changedBatchCells);

        return result;
    }

    private List<CellProgress> removeCaptureCell(
            List<CellProgress> capture,
            Long batteryCellId,
            Long batchId
    ) {
        return capture.stream()
                .filter(progress ->
                        !(
                                progress.batteryCellId().equals(batteryCellId)
                                        && progress.batchId().equals(batchId)
                        )
                )
                .toList();
    }
}
