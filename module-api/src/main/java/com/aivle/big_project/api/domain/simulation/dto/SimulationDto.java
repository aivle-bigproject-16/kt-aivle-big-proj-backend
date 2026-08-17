package com.aivle.big_project.api.domain.simulation.dto;

import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.InspectionBatchStatus;
import com.aivle.big_project.domain.inspection.InspectionStatus;
import com.aivle.big_project.domain.inspection.InspectionType;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class SimulationDto {

    private SimulationDto() {
        // DTO 묶음 클래스는 객체 생성이 필요 없습니다.
    }

    /**
     * POST /sim 요청 본문
     */
    public record StartRequest(
            @Positive int batchSize,
            @Positive int batteryCellCount,
            @Positive int captureSpeed,
            boolean resetBeforeStart
    ) {
    }

    /**
     * GET /sim 응답 및 WS /ws/sim 전송 데이터
     */
    public record SnapshotResponse(
            SimulationEvent event,
            int batchCount,
            int batteryCellCount,
            int captureSpeed,
            List<CellProgress> registered,
            List<CellProgress> capture,
            AnalysisProgress analyze,
            List<CellProgress> completed
    ) {
    }

    /**
     * 각 배터리 셀의 현재 검사 진행 상태
     */
    public record CellProgress(
            Long batteryCellId,
            Long batchId,
            InspectionBatchStatus status,
            FinalLabel finalLabel
    ) {
    }

    /**
     * 각 배터리 셀과 inspection의 현재 검사 진행 상태
     */
    public record AnalysisProgress(
            Long batteryCellId,
            Long inspectionId,
            Long batchId,
            InspectionType inspectionType,
            InspectionStatus status,
            FinalLabel finalLabel
    ) {
    }

    /**
     * 프론트에 알릴 시뮬레이션 이벤트 종류
     */
    public enum SimulationEvent {
        PROGRESS,
        COMPLETED
    }
}