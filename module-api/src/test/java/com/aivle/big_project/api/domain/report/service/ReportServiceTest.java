package com.aivle.big_project.api.domain.report.service;

import com.aivle.big_project.api.domain.report.dto.DailyReportCreateRequest;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.report.ReportStatus;
import com.aivle.big_project.domain.report.ReportsDaily;
import com.aivle.big_project.domain.report.ReportsDailyItemRepository;
import com.aivle.big_project.domain.report.ReportsDailyRepository;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 일일_리포트는_생성_트랜잭션이_커밋된_후_AI_워커에_전달한다() {
        ReportsDailyRepository dailyRepository = mock(ReportsDailyRepository.class);
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        ReportsDaily saved = mock(ReportsDaily.class);
        LocalDate reportDate = LocalDate.of(2026, 8, 14);

        when(dailyRepository.findByReportDate(reportDate)).thenReturn(Optional.empty());
        when(dailyRepository.save(org.mockito.ArgumentMatchers.any(ReportsDaily.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(42L);
        when(saved.getReportDate()).thenReturn(reportDate);
        when(saved.getStatus()).thenReturn(ReportStatus.PENDING);

        ReportService service = new ReportService(
                dailyRepository,
                mock(ReportsDailyItemRepository.class),
                mock(ReportsIndividualRepository.class),
                mock(BatteryCellRepository.class),
                mock(InspectionRepository.class),
                mock(DefectResultRepository.class),
                restClient
        );

        TransactionSynchronizationManager.initSynchronization();
        service.createDailyReport(new DailyReportCreateRequest(reportDate));

        verify(restClient, never()).post();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(restClient).post();
    }
}
