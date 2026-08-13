package com.aivle.big_project.api.global.s3;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Template s3Template;

    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new S3FileStorageService(s3Template);
        ReflectionTestUtils.setField(
                service,
                "bucketName",
                "notice-test-bucket"
        );
    }

    @Test
    void docx_파일을_허용하고_만료되지_않는_객체_키를_저장한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.DOCX",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx-content".getBytes()
        );

        String fileReference = service.uploadFile(file);

        assertThat(fileReference)
                .startsWith("notices/")
                .endsWith(".docx")
                .doesNotContain("?");
        verify(s3Template).upload(
                eq("notice-test-bucket"),
                startsWith("notices/"),
                any(InputStream.class)
        );
    }

    @Test
    void 응답마다_쿼리를_포함한_새_서명_URL을_발급한다() throws Exception {
        URL signedUrl = new URL(
                "https://notice-test-bucket.s3.amazonaws.com/notices/file.pdf"
                        + "?X-Amz-Signature=valid-signature"
        );
        when(s3Template.createSignedGetURL(
                "notice-test-bucket",
                "notices/file.pdf",
                Duration.ofDays(7)
        )).thenReturn(signedUrl);

        String downloadUrl = service.createDownloadUrl("notices/file.pdf");

        assertThat(downloadUrl)
                .isEqualTo(signedUrl.toString())
                .contains("?X-Amz-Signature=");
    }

    @Test
    void 기존_DB의_URL도_객체_키로_변환해_서명한다() throws Exception {
        String legacyUrl = "https://notice-test-bucket.s3.amazonaws.com/"
                + "notices/legacy.pdf";
        URL signedUrl = new URL(
                legacyUrl + "?X-Amz-Signature=refreshed-signature"
        );
        when(s3Template.createSignedGetURL(
                "notice-test-bucket",
                "notices/legacy.pdf",
                Duration.ofDays(7)
        )).thenReturn(signedUrl);

        assertThat(service.createDownloadUrl(legacyUrl))
                .isEqualTo(signedUrl.toString());
    }
}
