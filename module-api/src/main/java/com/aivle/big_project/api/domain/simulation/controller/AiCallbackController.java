package com.aivle.big_project.api.domain.simulation.controller;

import com.aivle.big_project.api.domain.simulation.client.config.AiGatewayProperties;
import com.aivle.big_project.api.domain.simulation.client.dto.AiServerDto;
import com.aivle.big_project.api.domain.simulation.service.AiCallbackService;
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
@RequestMapping("/internal/ai/callbacks")
@RequiredArgsConstructor
public class AiCallbackController {

    private final AiCallbackService aiCallbackService;
    private final AiGatewayProperties aiGatewayProperties;

    @PostMapping("/cell")
    public ResponseEntity<AiServerDto.CallbackResponse> receiveCellAnalysis(
            @RequestHeader(
                    name = "X-Internal-Api-Key",
                    required = false
            ) String internalApiKey,
            @RequestBody AiServerDto.CellAnalysisCallbackRequest request
    ) {
        validateInternalApiKey(internalApiKey);

        AiServerDto.CallbackResponse response =
                aiCallbackService.handle(request);

        return ResponseEntity.ok(response);
    }

    private void validateInternalApiKey(String internalApiKey) {
        if (internalApiKey == null
                || !internalApiKey.equals(
                aiGatewayProperties.internalApiKey()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 내부 API Key입니다."
            );
        }
    }
}