package com.aivle.big_project.api.domain.report.service;

import com.aivle.big_project.api.domain.report.dto.DailyReportCreateRequest;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionStatus;
import com.aivle.big_project.domain.inspection.InspectionType;
import com.aivle.big_project.domain.report.ReportStatus;
import com.aivle.big_project.domain.report.ReportsDaily;
import com.aivle.big_project.domain.report.ReportsDailyItemRepository;
import com.aivle.big_project.domain.report.ReportsDailyRepository;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import com.aivle.big_project.api.global.storage.S3ImageUrlService;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {

    S3ImageUrlService s3ImageUrlService =
            mock(S3ImageUrlService.class);

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
                restClient,
                s3ImageUrlService,
                new ObjectMapper()
        );

        TransactionSynchronizationManager.initSynchronization();
        service.createDailyReport(new DailyReportCreateRequest(reportDate));

        verify(restClient, never()).post();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(restClient).post();
    }

    @Test
    void 개별_리포트는_과거_REJECT가_아닌_최신_배치의_CT_RGB를_함께_고정한다() {
        ReportsIndividualRepository individualRepository = mock(ReportsIndividualRepository.class);
        BatteryCellRepository cellRepository = mock(BatteryCellRepository.class);
        InspectionRepository inspectionRepository = mock(InspectionRepository.class);
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        BatteryCell cell = mock(BatteryCell.class);
        InspectionBatch latestBatch = mock(InspectionBatch.class);
        InspectionBatch oldBatch = mock(InspectionBatch.class);
        Inspection ct = mock(Inspection.class);
        Inspection rgb = mock(Inspection.class);
        Inspection oldReject = mock(Inspection.class);

        when(cell.getId()).thenReturn(41L);
        when(cellRepository.findById(41L)).thenReturn(Optional.of(cell));
        when(latestBatch.getId()).thenReturn(20L);
        when(oldBatch.getId()).thenReturn(19L);
        when(ct.getId()).thenReturn(681L);
        when(rgb.getId()).thenReturn(682L);
        when(oldReject.getId()).thenReturn(640L);
        when(ct.getInspectionBatch()).thenReturn(latestBatch);
        when(rgb.getInspectionBatch()).thenReturn(latestBatch);
        when(oldReject.getInspectionBatch()).thenReturn(oldBatch);
        when(ct.getInspectionType()).thenReturn(InspectionType.CT);
        when(rgb.getInspectionType()).thenReturn(InspectionType.RGB);
        when(ct.getStatus()).thenReturn(InspectionStatus.COMPLETED);
        when(rgb.getStatus()).thenReturn(InspectionStatus.COMPLETED);
        when(inspectionRepository.findAllByBatteryCellIdOrderByBatchDesc(41L))
                .thenReturn(List.of(ct, rgb, oldReject));
        when(individualRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReportService service = new ReportService(
                mock(ReportsDailyRepository.class),
                mock(ReportsDailyItemRepository.class),
                individualRepository,
                cellRepository,
                inspectionRepository,
                mock(DefectResultRepository.class),
                restClient,
                s3ImageUrlService,
                new ObjectMapper()
        );

        TransactionSynchronizationManager.initSynchronization();
        service.createIndividualReport(
                new com.aivle.big_project.api.domain.report.dto.IndividualReportCreateRequest(41L, true)
        );

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.aivle.big_project.domain.report.ReportsIndividual.class
        );
        verify(individualRepository).save(captor.capture());
        assertThat(captor.getValue().getRepresentativeInspection()).isSameAs(ct);
        assertThat(captor.getValue().getSourceInspectionIds()).isEqualTo("[681,682]");
    }
}
