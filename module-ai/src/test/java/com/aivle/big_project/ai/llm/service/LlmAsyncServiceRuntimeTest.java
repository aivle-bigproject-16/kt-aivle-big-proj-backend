package com.aivle.big_project.ai.llm.service;

import com.aivle.big_project.ai.llm.client.LlmWebClient;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.report.ReportStatus;
import com.aivle.big_project.domain.report.ReportsDailyRepository;
import com.aivle.big_project.domain.report.ReportsIndividual;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmAsyncServiceRuntimeTest {

    @Mock
    private ReportsDailyRepository reportsDailyRepository;
    @Mock
    private ReportsIndividualRepository reportsIndividualRepository;
    @Mock
    private DefectResultRepository defectResultRepository;
    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private InspectionImageRepository inspectionImageRepository;
    @Mock
    private LlmWebClient llmWebClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @InjectMocks
    private LlmAsyncService service;

    @Test
    void dailyGenerationDoesNotHoldTransactionAcrossVlmCall()
            throws NoSuchMethodException {
        assertThat(LlmAsyncService.class
                .getMethod("generateDailyReportAsync", Long.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    @Test
    void individualGenerationDoesNotHoldTransactionAcrossVlmCall()
            throws NoSuchMethodException {
        assertThat(LlmAsyncService.class
                .getMethod("generateIndividualReportAsync", Long.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    @Test
    void unexpectedIndividualWorkerErrorTerminalizesReport() {
        TransactionStatus transactionStatus = org.mockito.Mockito.mock(
                TransactionStatus.class
        );
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(transactionStatus);
        ReportsIndividual report = org.mockito.Mockito.mock(
                ReportsIndividual.class
        );
        when(reportsIndividualRepository.findById(1L))
                .thenReturn(Optional.of(report));

        service.generateIndividualReportAsync(1L);

        verify(report).updateResult(
                ReportStatus.FAILED,
                null,
                null,
                "WORKER_ERROR:IllegalStateException"
        );
        verify(reportsIndividualRepository).save(report);
        verify(transactionManager).rollback(transactionStatus);
    }
}
