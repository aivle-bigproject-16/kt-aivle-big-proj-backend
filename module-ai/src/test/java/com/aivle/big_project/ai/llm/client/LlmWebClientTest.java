package com.aivle.big_project.ai.llm.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmWebClientTest {

    @Test
    void dailyReportTimeoutAllowsLongerGenerationThanIndividualReport() {
        assertThat(LlmWebClient.INDIVIDUAL_REPORT_TIMEOUT)
                .isEqualTo(Duration.ofMinutes(6));
        assertThat(LlmWebClient.DAILY_REPORT_TIMEOUT)
                .isEqualTo(Duration.ofMinutes(10));
    }
}
