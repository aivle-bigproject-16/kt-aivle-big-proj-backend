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

    private static final int MAX_BATCH_CAPTURE_ATTEMPTS = 3;
    private static final int MIN_CAPTURE_RETRY_DELAY_SECONDS = 3;
    private static final int MAX_RECAPTURE_TASK_ATTEMPTS = 3;

    private final SimulationService simulationService;
    private final InspectionBatchRepository inspectionBatchRepository;
    private final SimulationCaptureService simulationCaptureService;
    private final AiCallbackService aiCallbackService;

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
        scheduleRecapture(event, 1, event.captureSpeed());
    }

    private void scheduleRecapture(
            InspectionRecaptureRequestedEvent event,
            int attempt,
            long delaySeconds
    ) {
        simulationTaskScheduler.schedule(
                () -> {
                    try {
                        log.info(
                                "재촬영 시작. inspectionId={}, runId={}, attempt={}/{}",
                                event.inspectionId(),
                                event.simulationRunId(),
                                attempt,
                                MAX_RECAPTURE_TASK_ATTEMPTS
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
                                "재촬영 처리 실패. inspectionId={}, runId={}, attempt={}/{}",
                                event.inspectionId(),
                                event.simulationRunId(),
                                attempt,
                                MAX_RECAPTURE_TASK_ATTEMPTS,
                                exception
                        );

                        if (attempt < MAX_RECAPTURE_TASK_ATTEMPTS) {
                            scheduleRecapture(
                                    event,
                                    attempt + 1,
                                    Math.min(1L << attempt, 30L)
                            );
                            return;
                        }

                        aiCallbackService.failStuckRecapture(
                                event.inspectionId(),
                                "재촬영 작업이 %d회 실패했습니다: %s: %s"
                                        .formatted(
                                                MAX_RECAPTURE_TASK_ATTEMPTS,
                                                exception.getClass()
                                                        .getSimpleName(),
                                                exception.getMessage()
                                        )
                        );
                    }
                },
                Instant.now().plusSeconds(delaySeconds)
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

                    scheduleBatchCaptureCompletion(
                            simulationRunId,
                            batchIds,
                            batchIndex,
                            captureSpeed,
                            1,
                            captureSpeed
                    );
                },
                Instant.now()
        );
    }

    private void scheduleBatchCaptureCompletion(
            Long simulationRunId,
            List<Long> batchIds,
            int batchIndex,
            int captureSpeed,
            int attempt,
            int delaySeconds
    ) {
        Long batchId = batchIds.get(batchIndex);

        simulationTaskScheduler.schedule(
                () -> {
                    try {
                        log.info(
                                "배치 촬영 완료 처리 시작. runId={}, batchId={}, attempt={}/{}",
                                simulationRunId,
                                batchId,
                                attempt,
                                MAX_BATCH_CAPTURE_ATTEMPTS
                        );

                        completeCaptureAndScheduleNext(
                                simulationRunId,
                                batchIds,
                                batchIndex,
                                captureSpeed
                        );

                        log.info(
                                "배치 촬영 완료 처리 성공. runId={}, batchId={}, attempt={}/{}",
                                simulationRunId,
                                batchId,
                                attempt,
                                MAX_BATCH_CAPTURE_ATTEMPTS
                        );
                    } catch (Exception exception) {
                        retryBatchCaptureCompletion(
                                simulationRunId,
                                batchIds,
                                batchIndex,
                                captureSpeed,
                                attempt,
                                exception
                        );
                    }
                },
                Instant.now().plusSeconds(delaySeconds)
        );
    }

    private void retryBatchCaptureCompletion(
            Long simulationRunId,
            List<Long> batchIds,
            int batchIndex,
            int captureSpeed,
            int failedAttempt,
            Exception exception
    ) {
        Long batchId = batchIds.get(batchIndex);

        if (failedAttempt >= MAX_BATCH_CAPTURE_ATTEMPTS) {
            log.error(
                    "배치 촬영 완료 처리 최종 실패. runId={}, batchId={}, attempts={}",
                    simulationRunId,
                    batchId,
                    failedAttempt,
                    exception
            );
            return;
        }

        int nextAttempt = failedAttempt + 1;
        int retryDelaySeconds = Math.max(
                MIN_CAPTURE_RETRY_DELAY_SECONDS,
                captureSpeed
        );

        log.warn(
                "배치 촬영 완료 처리 실패. 재시도 예약. runId={}, batchId={}, " +
                        "failedAttempt={}/{}, nextAttempt={}, retryDelaySeconds={}",
                simulationRunId,
                batchId,
                failedAttempt,
                MAX_BATCH_CAPTURE_ATTEMPTS,
                nextAttempt,
                retryDelaySeconds,
                exception
        );

        scheduleBatchCaptureCompletion(
                simulationRunId,
                batchIds,
                batchIndex,
                captureSpeed,
                nextAttempt,
                retryDelaySeconds
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
