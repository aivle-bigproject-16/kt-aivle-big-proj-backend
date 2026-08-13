package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.client.AiGatewayClient;
import com.aivle.big_project.api.domain.simulation.client.config.AiGatewayProperties;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.StartRequest;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.simulation.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock private SimulationRunRepository simulationRunRepository;
    @Mock private BatteryCellRepository batteryCellRepository;
    @Mock private InspectionBatchRepository inspectionBatchRepository;
    @Mock private InspectionRepository inspectionRepository;
    @Mock private SimulationSnapshotStore simulationSnapshotStore;
    @Mock private SimulationEventPublisher simulationEventPublisher;
    @Mock private InspectionImageRepository inspectionImageRepository;
    @Mock private AiGatewayClient aiGatewayClient;
    @Mock private AiGatewayProperties aiGatewayProperties;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private BatteryCell batteryCell;

    private SimulationService service;

    @BeforeEach
    void setUp() {
        service = new SimulationService(
                simulationRunRepository,
                batteryCellRepository,
                inspectionBatchRepository,
                inspectionRepository,
                simulationSnapshotStore,
                simulationEventPublisher,
                inspectionImageRepository,
                aiGatewayClient,
                aiGatewayProperties,
                applicationEventPublisher
        );
    }

    @Test
    void startSelectsOnlyPurposeBuiltSimulationCells() {
        when(batteryCellRepository
                .findByCellSerialNoStartingWithOrderByCellSerialNoAsc(
                        eq("SIM-"),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(batteryCell));

        assertThatThrownBy(() -> service.start(new StartRequest(5, 20, 100)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SIM 시뮬레이션 셀 수가 부족합니다");

        verify(batteryCellRepository)
                .findByCellSerialNoStartingWithOrderByCellSerialNoAsc(
                        eq("SIM-"),
                        any(Pageable.class)
                );
    }
}
