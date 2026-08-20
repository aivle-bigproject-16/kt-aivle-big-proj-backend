package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.exception.RecaptureSourceNotFoundException;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.image.BatteryCellImage;
import com.aivle.big_project.domain.image.BatteryCellImageRepository;
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
        when(inspection.getCaptureRetryCount()).thenReturn(1);
        when(inspection.getBatteryCell()).thenReturn(batteryCell);
        when(batteryCell.getId()).thenReturn(20L);
    }

    @Test
    void firstRecaptureUsesRecaptureNumberOneSources() {
        when(inspection.getId()).thenReturn(10L);
        when(sourceImage.getId()).thenReturn(30L);
        when(batteryCellImageRepository
                .findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
                        20L,
                        "RGB",
                        1
                ))
                .thenReturn(List.of(sourceImage));

        service.recapture(10L);

        verify(inspectionImageRepository).save(any(InspectionImage.class));
        verify(inspectionImageRepository)
                .existsByInspectionIdAndBatteryCellImageIdAndAttemptNo(
                        10L,
                        30L,
                        2
                );
        verify(batteryCellImageRepository, never())
                .findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
                        20L,
                        "RGB",
                        0
                );
    }

    @Test
    void secondRecaptureUsesRecaptureNumberTwoSources() {
        when(inspection.getId()).thenReturn(10L);
        when(sourceImage.getId()).thenReturn(30L);
        when(inspection.currentAttemptNo()).thenReturn(3);
        when(inspection.getCaptureRetryCount()).thenReturn(2);
        when(batteryCellImageRepository
                .findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
                        20L,
                        "RGB",
                        2
                ))
                .thenReturn(List.of(sourceImage));

        service.recapture(10L);

        verify(inspectionImageRepository)
                .existsByInspectionIdAndBatteryCellImageIdAndAttemptNo(
                        10L,
                        30L,
                        3
                );
        verify(inspectionImageRepository).save(any(InspectionImage.class));
    }

    @Test
    void recaptureFailsInsteadOfReusingInitialSources() {
        when(batteryCellImageRepository
                .findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
                        20L,
                        "RGB",
                        1
                ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.recapture(10L))
                .isInstanceOf(RecaptureSourceNotFoundException.class)
                .hasMessageContaining(
                        "RGB 재촬영 원본 이미지가 없습니다. batteryCellId=20, recaptureNo=1"
                );

        verify(batteryCellImageRepository, never())
                .findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
                        20L,
                        "RGB",
                        0
                );
    }
}
