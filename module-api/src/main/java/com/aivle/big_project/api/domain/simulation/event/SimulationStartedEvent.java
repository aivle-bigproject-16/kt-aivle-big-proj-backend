package com.aivle.big_project.api.domain.simulation.event;

public record SimulationStartedEvent(
        Long simulationRunId,
        int captureSpeed
) {
}