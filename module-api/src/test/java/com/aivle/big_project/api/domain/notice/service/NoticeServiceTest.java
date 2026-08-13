package com.aivle.big_project.api.domain.notice.service;

import com.aivle.big_project.api.domain.notice.dto.NoticeDto;
import com.aivle.big_project.api.global.s3.S3FileStorageService;
import com.aivle.big_project.domain.notice.Notice;
import com.aivle.big_project.domain.notice.NoticeRepository;
import com.aivle.big_project.domain.user.User;
import com.aivle.big_project.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3FileStorageService s3FileStorageService;

    @Mock
    private User author;

    private NoticeService service;

    @BeforeEach
    void setUp() {
        service = new NoticeService(
                noticeRepository,
                userRepository,
                s3FileStorageService
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 파일_교체는_DB_커밋_후에만_기존_객체를_삭제한다() {
        Notice notice = noticeWithAttachment("notices/old.pdf");
        MockMultipartFile newFile = file("new.pdf");
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(s3FileStorageService.uploadFile(newFile))
                .thenReturn("notices/new.pdf");
        when(s3FileStorageService.createDownloadUrl("notices/new.pdf"))
                .thenReturn("https://signed.example/new.pdf?signature=valid");

        NoticeDto.Response response = service.updateNotice(
                1L,
                new NoticeDto.Request("title", "content", newFile, false)
        );

        verify(s3FileStorageService, never()).deleteFile("notices/old.pdf");
        assertThat(response.fileUrl()).contains("signature=valid");

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(s3FileStorageService).deleteFile("notices/old.pdf");
        verify(s3FileStorageService, never()).deleteFile("notices/new.pdf");
    }

    @Test
    void 파일_교체_트랜잭션이_롤백되면_새_객체만_정리한다() {
        Notice notice = noticeWithAttachment("notices/old.pdf");
        MockMultipartFile newFile = file("new.pdf");
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(s3FileStorageService.uploadFile(newFile))
                .thenReturn("notices/new.pdf");
        when(s3FileStorageService.createDownloadUrl("notices/new.pdf"))
                .thenReturn("https://signed.example/new.pdf?signature=valid");

        service.updateNotice(
                1L,
                new NoticeDto.Request("title", "content", newFile, false)
        );

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(s3FileStorageService).deleteFile("notices/new.pdf");
        verify(s3FileStorageService, never()).deleteFile("notices/old.pdf");
    }

    @Test
    void 파일_삭제도_DB_커밋_후에만_S3에_반영한다() {
        Notice notice = noticeWithAttachment("notices/old.pdf");
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        NoticeDto.Response response = service.updateNotice(
                1L,
                new NoticeDto.Request("title", "content", null, true)
        );

        assertThat(response.fileUrl()).isNull();
        verify(s3FileStorageService, never()).deleteFile("notices/old.pdf");

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(s3FileStorageService).deleteFile("notices/old.pdf");
    }

    @Test
    void 공지_DB_저장이_실패하면_업로드한_객체를_롤백_정리한다() {
        MockMultipartFile file = file("new.pdf");
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(author));
        when(s3FileStorageService.uploadFile(file))
                .thenReturn("notices/new.pdf");
        when(noticeRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.createNotice(
                "admin@example.com",
                new NoticeDto.Request("title", "content", file, false)
        )).isInstanceOf(IllegalStateException.class);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(s3FileStorageService).deleteFile("notices/new.pdf");
    }

    private Notice noticeWithAttachment(String fileReference) {
        Notice notice = Notice.builder()
                .title("old title")
                .content("old content")
                .author(author)
                .build();
        notice.updateFile(fileReference, "old.pdf");
        return notice;
    }

    private MockMultipartFile file(String fileName) {
        return new MockMultipartFile(
                "file",
                fileName,
                "application/pdf",
                "file-content".getBytes()
        );
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(sync -> sync.afterCompletion(status));
    }
}
