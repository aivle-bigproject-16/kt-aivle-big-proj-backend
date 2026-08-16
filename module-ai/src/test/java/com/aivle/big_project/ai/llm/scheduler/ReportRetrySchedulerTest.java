package com.aivle.big_project.ai.llm.scheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRetrySchedulerTest {

    @Test
    void staleWindowExceedsDailyReportTimeout() {
        assertThat(ReportRetryScheduler.PENDING_REPORT_STALE_MINUTES)
                .isGreaterThan(10L);
    }
}
