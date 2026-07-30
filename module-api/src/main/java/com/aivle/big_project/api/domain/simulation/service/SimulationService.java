package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.CellProgress;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SimulationEvent;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.StartRequest;
import com.aivle.big_project.domain.simulation.SimulationRun;
import com.aivle.big_project.domain.simulation.SimulationRunRepository;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.simulation.SimulationStatus;
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

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private final SimulationRunRepository simulationRunRepository;
    private final BatteryCellRepository batteryCellRepository;
    private final InspectionBatchRepository inspectionBatchRepository;
    private final InspectionRepository inspectionRepository;
    private final SimulationSnapshotStore simulationSnapshotStore;

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
                Inspection inspection = Inspection.create(
                        inspectionBatch,
                        batteryCell
                );

                inspectionRepository.save(inspection);

                if (startIndex == 0) {
                    registered.add(new CellProgress(
                            batteryCell.getId(),
                            inspection.getId(),
                            inspectionBatch.getId(),
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

        simulationSnapshotStore.save(snapshot);

        return snapshot;
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
}