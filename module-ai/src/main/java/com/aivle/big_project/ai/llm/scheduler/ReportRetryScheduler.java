package com.aivle.big_project.ai.llm.scheduler;

import com.aivle.big_project.ai.llm.service.LlmAsyncService;
import com.aivle.big_project.domain.report.ReportsDaily;
import com.aivle.big_project.domain.report.ReportsDailyRepository;
import com.aivle.big_project.domain.report.ReportsIndividual;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRetryScheduler {

    private final ReportsIndividualRepository reportsIndividualRepository;
    private final ReportsDailyRepository reportsDailyRepository;
    private final LlmAsyncService llmAsyncService;

    // 1분마다 실행 (60000ms)
    @Scheduled(fixedDelay = 60000)
    public void retryPendingReports() {
        // 기준 시간: 현재 시간보다 5분 전
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        log.info("[Scheduler] Checking for PENDING reports older than 5 minutes (Threshold: {})", threshold);

        // 1. 개별 리포트 재시도
        List<ReportsIndividual> pendingIndividual = reportsIndividualRepository.findPendingReportsOlderThan(threshold);
        if (!pendingIndividual.isEmpty()) {
            log.info("[Scheduler] Found {} zombie individual reports. Retrying...", pendingIndividual.size());
            for (ReportsIndividual report : pendingIndividual) {
                llmAsyncService.generateIndividualReportAsync(report.getId());
            }
        }

        // 2. 일일 리포트 재시도
        List<ReportsDaily> pendingDaily = reportsDailyRepository.findPendingReportsOlderThan(threshold);
        if (!pendingDaily.isEmpty()) {
            log.info("[Scheduler] Found {} zombie daily reports. Retrying...", pendingDaily.size());
            for (ReportsDaily report : pendingDaily) {
                llmAsyncService.generateDailyReportAsync(report.getId());
            }
        }
    }
}
