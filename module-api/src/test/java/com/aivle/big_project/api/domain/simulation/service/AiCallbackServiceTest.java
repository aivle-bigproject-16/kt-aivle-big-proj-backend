package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SimulationEvent;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.aivle.big_project.api.domain.simulation.event.InspectionAiRetryRequestedEvent;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.image.BatteryCellImageRepository;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionFailureType;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.inspection.InspectionStatus;
import com.aivle.big_project.domain.inspection.InspectionType;
import com.aivle.big_project.domain.simulation.SimulationRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallbackServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private BatteryCellImageRepository batteryCellImageRepository;

    @Mock
    private InspectionImageRepository inspectionImageRepository;

    @Mock
    private DefectResultRepository defectResultRepository;

    @Mock
    private SimulationSnapshotStore simulationSnapshotStore;

    @Mock
    private SimulationEventPublisher simulationEventPublisher;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private Inspection inspection;

    @Mock
    private InspectionBatch inspectionBatch;

    @Mock
    private SimulationRun simulationRun;

    @Mock
    private BatteryCell batteryCell;

    private AiCallbackService service;

    @BeforeEach
    void setUp() {
        service = new AiCallbackService(
                inspectionRepository,
                batteryCellImageRepository,
                inspectionImageRepository,
                defectResultRepository,
                simulationSnapshotStore,
                simulationEventPublisher,
                new ObjectMapper().findAndRegisterModules(),
                applicationEventPublisher
        );
    }

    @Test
    void 긴_실패_사유를_원문에_보존하고_제한된_컬럼에는_요약만_저장한다() {
        String requestId = "callback-request-id";
        String failureReason = "S3 image download failed. ".repeat(20);

        AiServerDto.CellAnalysisCallbackRequest callback =
                new AiServerDto.CellAnalysisCallbackRequest(
                        requestId,
                        31L,
                        11L,
                        21L,
                        "CELL-0001",
                        "FAILED",
                        null,
                        "AI",
                        failureReason,
                        BigDecimal.ZERO,
                        Instant.parse("2026-08-14T00:00:00Z"),
                        List.of()
                );

        when(inspectionRepository.findByAiRequestId(requestId))
                .thenReturn(Optional.of(inspection));
        when(inspection.getId()).thenReturn(11L);
        when(inspection.getBatteryCell()).thenReturn(batteryCell);
        when(batteryCell.getId()).thenReturn(21L);
        when(inspection.getStatus()).thenReturn(InspectionStatus.ANALYZING);
        when(inspection.getInspectionType()).thenReturn(InspectionType.CT);
        when(inspection.currentAttemptNo()).thenReturn(1);
        when(inspection.getInspectionBatch()).thenReturn(inspectionBatch);
        when(inspectionBatch.getId()).thenReturn(31L);
        when(inspectionBatch.getSimulationRun()).thenReturn(simulationRun);
        when(simulationRun.getId()).thenReturn(41L);
        when(simulationRun.getBatchCount()).thenReturn(1);
        when(simulationRun.getBatteryCellCount()).thenReturn(1);
        when(simulationRun.getCaptureSpeed()).thenReturn(1000);
        when(inspectionRepository
                .existsByInspectionBatchIdAndStatusIn(eq(31L), anyList()))
                .thenReturn(true);
        when(inspectionRepository
                .existsByInspectionBatchSimulationRunIdAndStatusIn(
                        eq(41L),
                        anyList()
                ))
                .thenReturn(true);
        when(inspectionRepository
                .findByInspectionBatchIdAndBatteryCellIdOrderByIdAsc(
                        31L,
                        21L
                ))
                .thenReturn(List.of());
        when(simulationSnapshotStore.find()).thenReturn(Optional.empty());

        AiServerDto.CallbackResponse response = service.handle(callback);

        ArgumentCaptor<DefectResult> resultCaptor =
                ArgumentCaptor.forClass(DefectResult.class);
        verify(defectResultRepository).save(resultCaptor.capture());

        DefectResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getDefectType()).isEqualTo("AI");
        assertThat(savedResult.getRawResponse()).contains(failureReason);
        assertThat(response.received()).isTrue();
        assertThat(response.savedResultCount()).isEqualTo(1);

        verify(inspection).completeAnalysis(
                InspectionStatus.FAILED,
                FinalLabel.FAIL,
                InspectionFailureType.AI,
                failureReason.substring(0, 100)
        );
    }

    @Test
    void AI_실패이고_재시도_횟수가_남으면_같은_검사의_재분석을_예약한다() {
        String requestId = "first-ai-request";
        AiServerDto.CellAnalysisCallbackRequest callback =
                new AiServerDto.CellAnalysisCallbackRequest(
                        requestId,
                        31L,
                        11L,
                        21L,
                        "CELL-0001",
                        "FAILED",
                        null,
                        "AI",
                        "MODEL_INFERENCE_ERROR",
                        BigDecimal.ZERO,
                        Instant.parse("2026-08-20T00:00:00Z"),
                        List.of()
                );

        SnapshotResponse current = new SnapshotResponse(
                SimulationEvent.PROGRESS,
                1,
                1,
                3,
                List.of(),
                List.of(),
                null,
                List.of()
        );

        when(inspectionRepository.findByAiRequestId(requestId))
                .thenReturn(Optional.of(inspection));
        when(inspection.getId()).thenReturn(11L);
        when(inspection.getBatteryCell()).thenReturn(batteryCell);
        when(batteryCell.getId()).thenReturn(21L);
        when(inspection.getStatus()).thenReturn(InspectionStatus.ANALYZING);
        when(inspection.getInspectionType()).thenReturn(InspectionType.CT);
        when(inspection.currentAttemptNo()).thenReturn(1);
        when(inspection.getInspectionBatch()).thenReturn(inspectionBatch);
        when(inspectionBatch.getId()).thenReturn(31L);
        when(inspectionBatch.getSimulationRun()).thenReturn(simulationRun);
        when(simulationRun.getId()).thenReturn(41L);
        when(simulationRun.getBatchCount()).thenReturn(1);
        when(simulationRun.getBatteryCellCount()).thenReturn(1);
        when(simulationRun.getCaptureSpeed()).thenReturn(3);
        when(inspection.canRetryAi(2)).thenReturn(true);
        when(simulationSnapshotStore.find()).thenReturn(Optional.of(current));

        AiServerDto.CallbackResponse response = service.handle(callback);

        verify(inspection).prepareAiRetry(
                InspectionFailureType.AI,
                "MODEL_INFERENCE_ERROR"
        );

        ArgumentCaptor<InspectionAiRetryRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(InspectionAiRetryRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue().simulationRunId()).isEqualTo(41L);
        assertThat(eventCaptor.getValue().inspectionId()).isEqualTo(11L);
        assertThat(response.message()).contains("재분석");
    }
}
