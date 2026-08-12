package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.event.InspectionAnalysisCompletedEvent;
import com.aivle.big_project.api.domain.simulation.event.SimulationStartedEvent;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import com.aivle.big_project.api.domain.simulation.event.InspectionRecaptureRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationFlowOrchestrator {

    private final SimulationService simulationService;
    private final InspectionBatchRepository inspectionBatchRepository;
    private final SimulationCaptureService simulationCaptureService;

    @Qualifier("simulationTaskScheduler")
    private final TaskScheduler simulationTaskScheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSimulationStarted(
            SimulationStartedEvent event
    ) {
        List<Long> batchIds = inspectionBatchRepository
                .findBySimulationRunIdOrderByIdAsc(event.simulationRunId())
                .stream()
                .map(InspectionBatch::getId)
                .toList();

        if (batchIds.isEmpty()) {
            return;
        }

        scheduleBatchCapture(
                event.simulationRunId(),
                batchIds,
                0,
                event.captureSpeed()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInspectionAnalysisCompleted(
            InspectionAnalysisCompletedEvent event
    ) {
        simulationTaskScheduler.schedule(
                () -> simulationService.startNextAnalysis(
                        event.simulationRunId()
                ),
                Instant.now()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInspectionRecaptureRequested(
            InspectionRecaptureRequestedEvent event
    ) {
        simulationTaskScheduler.schedule(
                () -> {
                    try {
                        log.info(
                                "재촬영 시작. inspectionId={}, runId={}",
                                event.inspectionId(),
                                event.simulationRunId()
                        );

                        simulationCaptureService.recapture(
                                event.inspectionId()
                        );

                        simulationService.completeInspectionRecapture(
                                event.inspectionId()
                        );

                        log.info(
                                "재촬영 완료. inspectionId={}",
                                event.inspectionId()
                        );

                        simulationService.startNextAnalysis(
                                event.simulationRunId()
                        );
                    } catch (Exception exception) {
                        log.error(
                                "재촬영 처리 실패. inspectionId={}, runId={}",
                                event.inspectionId(),
                                event.simulationRunId(),
                                exception
                        );
                    }
                },
                Instant.now().plusSeconds(event.captureSpeed())
        );
    }

    private void scheduleBatchCapture(
            Long simulationRunId,
            List<Long> batchIds,
            int batchIndex,
            int captureSpeed
    ) {
        if (batchIndex >= batchIds.size()) {
            return;
        }

        Long batchId = batchIds.get(batchIndex);

        simulationTaskScheduler.schedule(
                () -> {
                    simulationService.startBatchCapture(batchId);

                    simulationTaskScheduler.schedule(
                            () -> completeCaptureAndScheduleNext(
                                    simulationRunId,
                                    batchIds,
                                    batchIndex,
                                    captureSpeed
                            ),
                            Instant.now().plusSeconds(captureSpeed)
                    );
                },
                Instant.now()
        );
    }


    private void completeCaptureAndScheduleNext(
            Long simulationRunId,
            List<Long> batchIds,
            int batchIndex,
            int captureSpeed
    ) {
        Long batchId = batchIds.get(batchIndex);

        simulationCaptureService.capture(batchId);
        simulationService.completeBatchCapture(batchId);

        // AI가 비어 있으면 Run 전체에서 다음 Inspection 한 건 분석 시작
        simulationService.startNextAnalysis(simulationRunId);

        // AI 완료를 기다리지 않고 다음 Batch 촬영 시작
        scheduleBatchCapture(
                simulationRunId,
                batchIds,
                batchIndex + 1,
                captureSpeed
        );
    }
}