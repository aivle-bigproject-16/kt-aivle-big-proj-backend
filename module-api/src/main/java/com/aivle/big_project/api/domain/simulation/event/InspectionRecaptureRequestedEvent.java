package com.aivle.big_project.api.domain.simulation.event;

public record InspectionRecaptureRequestedEvent(
        Long simulationRunId,
        Long inspectionId,
        int captureSpeed
) {
}