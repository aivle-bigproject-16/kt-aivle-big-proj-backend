package com.aivle.big_project.ai.gateway.service;

import com.aivle.big_project.ai.gateway.dto.AiCellAnalysisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class AiServerForwardService {

    private final WebClient aiServerWebClient;

    public AiCellAnalysisDto.AcceptedResponse forwardCellAnalysis(
            AiCellAnalysisDto.CellAnalysisRequest request
    ) {
        try {
            AiCellAnalysisDto.AcceptedResponse response = aiServerWebClient
                    .post()
                    .uri("/ai/cells/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiCellAnalysisDto.AcceptedResponse.class)
                    .block();

            if (response == null || !response.accepted()) {
                throw new IllegalStateException(
                        "Python AI 서버가 분석 요청을 접수하지 않았습니다."
                );
            }

            return response;

        } catch (WebClientResponseException exception) {
            throw new IllegalStateException(
                    "Python AI 서버 응답 오류: " + exception.getStatusCode(),
                    exception
            );

        } catch (WebClientRequestException exception) {
            throw new IllegalStateException(
                    "Python AI 서버 연결 또는 응답 시간 초과",
                    exception
            );
        }
    }
}