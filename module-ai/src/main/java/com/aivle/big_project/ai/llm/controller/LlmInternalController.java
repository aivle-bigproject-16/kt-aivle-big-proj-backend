package com.aivle.big_project.ai.llm.controller;

import com.aivle.big_project.ai.llm.service.LlmAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/llm")
@RequiredArgsConstructor
public class LlmInternalController {

    private final LlmAsyncService llmAsyncService;

    @PostMapping("/reports/daily/{reportId}")
    public ResponseEntity<Void> generateDailyReport(@PathVariable Long reportId) {
        log.info("Received internal request to generate daily report for ID: {}", reportId);
        
        // 비동기 서비스 호출 (메서드는 즉시 리턴됨)
        llmAsyncService.generateDailyReportAsync(reportId);
        
        return ResponseEntity.accepted().build(); // 202 Accepted 반환
    }

    @PostMapping("/reports/individual/{reportId}")
    public ResponseEntity<Void> generateIndividualReport(@PathVariable Long reportId) {
        log.info("Received internal request to generate individual report for ID: {}", reportId);
        
        llmAsyncService.generateIndividualReportAsync(reportId);
        
        return ResponseEntity.accepted().build();
    }
}
