package com.aivle.big_project.ai.gateway.dto;

import java.time.Instant;
import java.util.List;

public final class AiCellAnalysisDto {

    private AiCellAnalysisDto() {
    }

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

    public record AcceptedResponse(
            boolean accepted,
            String requestId,
            Long inspectionId,
            Long batteryCellId,
            String status,
            Instant acceptedAt
    ) {
    }
}