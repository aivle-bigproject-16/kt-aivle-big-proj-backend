package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimulationEventPublisher {

    private static final String SIMULATION_TOPIC = "/topic/sim";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(SnapshotResponse snapshot) {
        messagingTemplate.convertAndSend(
                SIMULATION_TOPIC,
                snapshot
        );
    }
}