package com.aivle.big_project.ai.llm.service;

import com.aivle.big_project.ai.llm.client.LlmWebClient;
import com.aivle.big_project.ai.llm.dto.VlmReportResponse;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.report.ReportStatus;
import com.aivle.big_project.domain.report.ReportsDaily;
import com.aivle.big_project.domain.report.ReportsDailyRepository;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmAsyncServiceTest {

    @Test
    void 다른_워커가_선점한_일일_리포트는_중복_생성하지_않는다() {
        ReportsDailyRepository dailyRepository = mock(ReportsDailyRepository.class);
        LlmWebClient llmWebClient = mock(LlmWebClient.class);
        when(dailyRepository.claimForGeneration(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        LlmAsyncService service = createService(dailyRepository, llmWebClient, mock(InspectionRepository.class), mock(DefectResultRepository.class));

        service.generateDailyReportAsync(7L);

        verify(dailyRepository, never()).findById(7L);
        verify(llmWebClient, never()).requestDailyReport(any(), eq(7L));
    }

    @Test
    void VLM_응답_후_리포트를_다시_조회해_최신_엔티티에_결과를_저장한다() {
        ReportsDailyRepository dailyRepository = mock(ReportsDailyRepository.class);
        InspectionRepository inspectionRepository = mock(InspectionRepository.class);
        DefectResultRepository defectRepository = mock(DefectResultRepository.class);
        LlmWebClient llmWebClient = mock(LlmWebClient.class);
        ReportsDaily initialReport = mock(ReportsDaily.class);
        ReportsDaily freshReport = mock(ReportsDaily.class);
        LocalDate reportDate = LocalDate.of(2026, 8, 14);
        VlmReportResponse response = new VlmReportResponse("COMPLETED", "title", "content", null);

        when(dailyRepository.claimForGeneration(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(dailyRepository.findById(9L))
                .thenReturn(Optional.of(initialReport), Optional.of(freshReport));
        when(initialReport.getReportDate()).thenReturn(reportDate);
        when(defectRepository.findDefectResultType(reportDate, reportDate, 10)).thenReturn(List.of());
        when(llmWebClient.requestDailyReport(any(), eq(9L))).thenReturn(Mono.just(response));

        LlmAsyncService service = createService(dailyRepository, llmWebClient, inspectionRepository, defectRepository);

        service.generateDailyReportAsync(9L);

        verify(dailyRepository, times(2)).findById(9L);
        verify(freshReport).updateResult(ReportStatus.COMPLETED, "title", "content", null);
        verify(freshReport).updateSummaryJson(any());
        verify(dailyRepository).save(freshReport);
        verify(dailyRepository, never()).save(initialReport);
    }

    private LlmAsyncService createService(
            ReportsDailyRepository dailyRepository,
            LlmWebClient llmWebClient,
            InspectionRepository inspectionRepository,
            DefectResultRepository defectRepository
    ) {
        return new LlmAsyncService(
                dailyRepository,
                mock(ReportsIndividualRepository.class),
                defectRepository,
                inspectionRepository,
                mock(InspectionImageRepository.class),
                llmWebClient,
                new ObjectMapper()
        );
    }
}
