package com.aivle.big_project.ai.llm.client;

import com.aivle.big_project.ai.llm.dto.VlmDailyReportRequest;
import com.aivle.big_project.ai.llm.dto.VlmIndividualReportRequest;
import com.aivle.big_project.ai.llm.dto.VlmReportResponse;
import com.aivle.big_project.domain.log.ApiLog;
import com.aivle.big_project.domain.log.ApiLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmWebClient {

    static final Duration INDIVIDUAL_REPORT_TIMEOUT = Duration.ofMinutes(6);
    static final Duration DAILY_REPORT_TIMEOUT = Duration.ofMinutes(10);

    private final WebClient webClient;
    private final ApiLogRepository apiLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${llm-server.base-url}")
    private String llmServerUrl;

    public Mono<VlmReportResponse> requestIndividualReport(VlmIndividualReportRequest request, Long reportId) {
        String url = llmServerUrl + "/vlm/reports/individual";
        log.info("Requesting VLM for individual report. URL: {}", url);

        long startTime = System.currentTimeMillis();

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VlmReportResponse.class)
                .timeout(INDIVIDUAL_REPORT_TIMEOUT)
                .doOnSuccess(response -> logApi(url, request, response, 200, System.currentTimeMillis() - startTime, null))
                .doOnError(error -> logApi(url, request, null, 500, System.currentTimeMillis() - startTime, error.getMessage()));
    }

    public Mono<VlmReportResponse> requestDailyReport(VlmDailyReportRequest request, Long reportId) {
        String url = llmServerUrl + "/vlm/reports/daily";
        log.info("Requesting VLM for daily report. URL: {}", url);

        long startTime = System.currentTimeMillis();

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VlmReportResponse.class)
                .timeout(DAILY_REPORT_TIMEOUT)
                .doOnSuccess(response -> logApi(url, request, response, 200, System.currentTimeMillis() - startTime, null))
                .doOnError(error -> logApi(url, request, null, 500, System.currentTimeMillis() - startTime, error.getMessage()));
    }

    private void logApi(String url, Object req, Object res, int status, long latency, String errorMsg) {
        try {
            String reqJson = req != null ? objectMapper.writeValueAsString(req) : null;
            String resJson = res != null ? objectMapper.writeValueAsString(res) : null;

            ApiLog logEntry = ApiLog.builder()
                    .direction("BE_TO_LLM")
                    .endpoint(url)
                    .httpStatus(status)
                    .latencyMs((int) latency)
                    .requestBody(reqJson)
                    .responseBody(resJson)
                    .errorMessage(errorMsg)
                    .build();

            apiLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to log API communication", e);
        }
    }
}
