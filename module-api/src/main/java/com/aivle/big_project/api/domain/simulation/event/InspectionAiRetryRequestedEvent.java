package com.aivle.big_project.api.domain.simulation.event;

public record InspectionAiRetryRequestedEvent(
        Long simulationRunId,
        Long inspectionId
) {
}
