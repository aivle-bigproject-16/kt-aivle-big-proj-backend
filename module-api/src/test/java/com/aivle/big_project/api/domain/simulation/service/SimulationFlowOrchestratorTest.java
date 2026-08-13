package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.event.SimulationStartedEvent;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationFlowOrchestratorTest {

    @Mock
    private SimulationService simulationService;

    @Mock
    private InspectionBatchRepository inspectionBatchRepository;

    @Mock
    private SimulationCaptureService simulationCaptureService;

    @Mock
    private TaskScheduler simulationTaskScheduler;

    @Mock
    private InspectionBatch inspectionBatch;

    private final Queue<Runnable> scheduledTasks = new ArrayDeque<>();

    private SimulationFlowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new SimulationFlowOrchestrator(
                simulationService,
                inspectionBatchRepository,
                simulationCaptureService,
                simulationTaskScheduler
        );

        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            return null;
        }).when(simulationTaskScheduler).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void retriesBatchCaptureAndContinuesFlowAfterSuccess() {
        Long simulationRunId = 1L;
        Long batchId = 10L;

        when(inspectionBatch.getId()).thenReturn(batchId);
        when(inspectionBatchRepository.findBySimulationRunIdOrderByIdAsc(simulationRunId))
                .thenReturn(List.of(inspectionBatch));

        doThrow(new RuntimeException("temporary capture failure"))
                .doNothing()
                .when(simulationCaptureService)
                .capture(batchId);

        orchestrator.handleSimulationStarted(
                new SimulationStartedEvent(simulationRunId, 5)
        );

        runNextScheduledTask();
        verify(simulationService).startBatchCapture(batchId);

        runNextScheduledTask();
        verify(simulationCaptureService).capture(batchId);
        verify(simulationService, never()).completeBatchCapture(batchId);
        assertThat(scheduledTasks).hasSize(1);

        runNextScheduledTask();
        verify(simulationCaptureService, org.mockito.Mockito.times(2)).capture(batchId);
        verify(simulationService).completeBatchCapture(batchId);
        verify(simulationService).startNextAnalysis(simulationRunId);
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void stopsFlowAfterMaximumBatchCaptureAttempts() {
        Long simulationRunId = 2L;
        Long batchId = 20L;

        when(inspectionBatch.getId()).thenReturn(batchId);
        when(inspectionBatchRepository.findBySimulationRunIdOrderByIdAsc(simulationRunId))
                .thenReturn(List.of(inspectionBatch));
        doThrow(new RuntimeException("persistent capture failure"))
                .when(simulationCaptureService)
                .capture(batchId);

        orchestrator.handleSimulationStarted(
                new SimulationStartedEvent(simulationRunId, 5)
        );

        runNextScheduledTask();
        runNextScheduledTask();
        runNextScheduledTask();
        runNextScheduledTask();

        verify(simulationCaptureService, org.mockito.Mockito.times(3)).capture(batchId);
        verify(simulationService, never()).completeBatchCapture(batchId);
        verify(simulationService, never()).startNextAnalysis(simulationRunId);
        assertThat(scheduledTasks).isEmpty();
    }

    private void runNextScheduledTask() {
        Runnable task = scheduledTasks.remove();
        task.run();
    }
}
