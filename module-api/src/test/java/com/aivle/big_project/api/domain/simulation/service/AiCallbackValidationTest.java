package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.image.BatteryCellImageRepository;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.inspection.InspectionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallbackValidationTest {

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
    private ObjectMapper objectMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks
    private AiCallbackService service;

    @Test
    void rejectsUnsupportedCallbackStatusBeforePersistingResults() {
        AiServerDto.CellAnalysisCallbackRequest callback = callback(
                "UNKNOWN",
                null,
                List.of()
        );
        prepareInspection(callback);

        assertThatThrownBy(() -> service.handle(callback))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(
                        ((ResponseStatusException) error).getStatusCode()
                ).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsCompletedCallbackWithoutFinalLabel() {
        AiServerDto.CellAnalysisCallbackRequest callback = callback(
                "COMPLETED",
                null,
                List.of()
        );
        prepareInspection(callback);

        assertThatThrownBy(() -> service.handle(callback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMPLETED 콜백에는 finalLabel이 필요합니다");
    }

    @Test
    void failureResultStoresFailureTypeInsteadOfLongReasonAsDefectType() {
        Inspection inspection = org.mockito.Mockito.mock(Inspection.class);
        when(inspection.getInspectionType()).thenReturn(InspectionType.CT);
        AiServerDto.CellAnalysisCallbackRequest callback =
                new AiServerDto.CellAnalysisCallbackRequest(
                        "qa-request",
                        10L,
                        20L,
                        30L,
                        "QA-CELL",
                        "FAILED",
                        null,
                        "CAPTURE",
                        "failure detail ".repeat(20),
                        BigDecimal.ONE,
                        Instant.parse("2026-08-15T00:00:00Z"),
                        List.of()
                );

        DefectResult result = ReflectionTestUtils.invokeMethod(
                service,
                "createFailureResult",
                inspection,
                callback
        );

        assertThat(result).isNotNull();
        assertThat(result.getDefectType()).isEqualTo("CAPTURE");
    }

    private void prepareInspection(
            AiServerDto.CellAnalysisCallbackRequest callback
    ) {
        Inspection inspection = org.mockito.Mockito.mock(Inspection.class);
        BatteryCell batteryCell = org.mockito.Mockito.mock(BatteryCell.class);
        when(inspectionRepository.findByAiRequestId(callback.requestId()))
                .thenReturn(Optional.of(inspection));
        when(inspection.getId()).thenReturn(callback.inspectionId());
        when(inspection.getBatteryCell()).thenReturn(batteryCell);
        when(batteryCell.getId()).thenReturn(callback.batteryCellId());
    }

    private AiServerDto.CellAnalysisCallbackRequest callback(
            String cellStatus,
            String finalLabel,
            List<AiServerDto.ImageAnalysisResult> imageResults
    ) {
        return new AiServerDto.CellAnalysisCallbackRequest(
                "qa-request",
                10L,
                20L,
                30L,
                "QA-CELL",
                cellStatus,
                finalLabel,
                null,
                null,
                BigDecimal.ONE,
                Instant.parse("2026-08-15T00:00:00Z"),
                imageResults
        );
    }
}
