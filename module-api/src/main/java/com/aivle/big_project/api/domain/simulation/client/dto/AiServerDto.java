package com.aivle.big_project.api.domain.simulation.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AiServerDto {

    private AiServerDto() {
    }

    // 백엔드 → AI 서버: POST /ai/cells/analyze
    public record CellAnalysisRequest(
            String requestId,
            Long batchId,
            Long inspectionId,
            Long batteryCellId,
            String cellSerialNo,
            Instant requestedAt,
            String callbackUrl,
            List<ImageRequest> images
    ) {
    }

    public record ImageRequest(
            Long imageId,
            String imageType,
            String bucketName,
            String objectKey
    ) {
    }

    // AI 서버 → 백엔드: 202 ACCEPTED
    public record AcceptedResponse(
            boolean accepted,
            String requestId,
            Long inspectionId,
            Long batteryCellId,
            String status,
            Instant acceptedAt
    ) {
    }

    // AI 서버 → 백엔드: POST /internal/ai/callbacks/cell
    public record CellAnalysisCallbackRequest(
            String requestId,
            Long batchId,
            Long inspectionId,
            Long batteryCellId,
            String cellSerialNo,
            String cellStatus,
            String finalLabel,
            String failureType,   // 추가
            String failureReason,
            BigDecimal confidence,
            Instant completedAt,
            List<ImageAnalysisResult> imageResults
    ) {
    }

    public record ImageAnalysisResult(
            Long imageId,
            String imageType,
            String label,
            BigDecimal confidence,
            List<Defect> defects,
            JsonNode rawResponse,
            Integer latencyMs,
            String errorCode,
            String errorMessage
    ) {
    }

    public record Defect(
            String defectType,
            BigDecimal confidence,
            BoundingBox bbox
    ) {
    }

    public record BoundingBox(
            Integer x,
            Integer y,
            Integer width,
            Integer height
    ) {
    }

    // 백엔드 → AI 서버 콜백 응답
    public record CallbackResponse(
            boolean received,
            String requestId,
            Long batchId,
            Long batteryCellId,
            int savedResultCount,
            boolean duplicate,
            String message
    ) {
    }
}