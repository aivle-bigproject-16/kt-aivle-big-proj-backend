package com.aivle.big_project.ai.gateway.controller;

import com.aivle.big_project.ai.gateway.config.AiServerProperties;
import com.aivle.big_project.ai.gateway.dto.AiCellAnalysisDto;
import com.aivle.big_project.ai.gateway.service.AiServerForwardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiServerForwardService aiServerForwardService;
    private final AiServerProperties aiServerProperties;

    @PostMapping("/cells/analyze")
    public ResponseEntity<AiCellAnalysisDto.AcceptedResponse> analyzeCell(
            @RequestHeader(
                    name = "X-Internal-Api-Key",
                    required = false
            ) String internalApiKey,
            @RequestBody AiCellAnalysisDto.CellAnalysisRequest request
    ) {
        validateInternalApiKey(internalApiKey);

        AiCellAnalysisDto.AcceptedResponse response =
                aiServerForwardService.forwardCellAnalysis(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    private void validateInternalApiKey(String internalApiKey) {
        if (internalApiKey == null
                || !internalApiKey.equals(
                aiServerProperties.internalApiKey()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 내부 API Key입니다."
            );
        }
    }
}