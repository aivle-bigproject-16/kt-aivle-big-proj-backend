package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.image.BatteryCellImage;
import com.aivle.big_project.domain.image.BatteryCellImageRepository;
import com.aivle.big_project.domain.image.CaptureSet;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.inspection.InspectionStatus;
import com.aivle.big_project.domain.inspection.InspectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationCaptureServiceTest {

    @Mock private InspectionBatchRepository inspectionBatchRepository;
    @Mock private InspectionRepository inspectionRepository;
    @Mock private InspectionImageRepository inspectionImageRepository;
    @Mock private BatteryCellImageRepository batteryCellImageRepository;
    @Mock private Inspection inspection;
    @Mock private BatteryCell batteryCell;
    @Mock private BatteryCellImage sourceImage;

    private SimulationCaptureService service;

    @BeforeEach
    void setUp() {
        service = new SimulationCaptureService(
                inspectionBatchRepository,
                inspectionRepository,
                inspectionImageRepository,
                batteryCellImageRepository
        );
        when(inspectionRepository.findById(10L))
                .thenReturn(Optional.of(inspection));
        when(inspection.getStatus()).thenReturn(InspectionStatus.CAPTURING);
        when(inspection.getInspectionType()).thenReturn(InspectionType.RGB);
        when(inspection.currentAttemptNo()).thenReturn(2);
        when(inspection.getBatteryCell()).thenReturn(batteryCell);
        when(batteryCell.getId()).thenReturn(20L);
    }

    @Test
    void recaptureUsesDedicatedRecaptureSources() {
        when(batteryCellImageRepository
                .findByBatteryCellIdAndImageTypeAndCaptureSetOrderByIdAsc(
                        20L,
                        "RGB",
                        CaptureSet.RECAPTURE
                ))
                .thenReturn(List.of(sourceImage));

        service.recapture(10L);

        verify(inspectionImageRepository).save(any(InspectionImage.class));
        verify(batteryCellImageRepository, never())
                .findByBatteryCellIdAndImageTypeAndCaptureSetOrderByIdAsc(
                        20L,
                        "RGB",
                        CaptureSet.INITIAL
                );
    }

    @Test
    void simulationRecaptureFailsInsteadOfReusingInitialSources() {
        when(batteryCell.getCellSerialNo()).thenReturn("SIM-0001");
        when(batteryCellImageRepository
                .findByBatteryCellIdAndImageTypeAndCaptureSetOrderByIdAsc(
                        20L,
                        "RGB",
                        CaptureSet.RECAPTURE
                ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.recapture(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RGB RECAPTURE 원본 이미지가 없습니다");

        verify(batteryCellImageRepository, never())
                .findByBatteryCellIdAndImageTypeAndCaptureSetOrderByIdAsc(
                        20L,
                        "RGB",
                        CaptureSet.INITIAL
                );
    }
}
