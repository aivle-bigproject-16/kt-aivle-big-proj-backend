package com.aivle.big_project.api.global.s3;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService {

    private final S3Template s3Template;

    @Value("${AWS_S3_BUCKET:kt-aivle-big-proj-bucket}")
    private String bucketName;

    // 허용된 확장자 및 MIME 타입 (화이트리스트)
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "png", "jpg", "jpeg", "docx");
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String NOTICE_PREFIX = "notices/";
    private static final Duration DOWNLOAD_URL_DURATION = Duration.ofDays(7);

    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        extension = extension.toLowerCase(Locale.ROOT);
        
        // UUID 난수화 (경로 추측 방지)
        String storedFileName = NOTICE_PREFIX + UUID.randomUUID() + "." + extension;

        try (InputStream is = file.getInputStream()) {
            s3Template.upload(bucketName, storedFileName, is);
            return storedFileName;
        } catch (IOException e) {
            log.error("Failed to upload file to S3", e);
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.");
        }
    }

    public String createDownloadUrl(String fileReference) {
        if (!StringUtils.hasText(fileReference)) {
            return null;
        }

        String objectKey = extractObjectKey(fileReference);
        return s3Template.createSignedGetURL(
                bucketName,
                objectKey,
                DOWNLOAD_URL_DURATION
        ).toString();
    }

    public void deleteFile(String fileReference) {
        if (!StringUtils.hasText(fileReference)) {
            return;
        }

        try {
            s3Template.deleteObject(bucketName, extractObjectKey(fileReference));
        } catch (Exception e) {
            log.warn("Failed to delete file from S3: {}", fileReference, e);
        }
    }

    private String extractObjectKey(String fileReference) {
        String objectKey = fileReference;

        if (fileReference.startsWith("http://")
                || fileReference.startsWith("https://")) {
            objectKey = URI.create(fileReference).getPath();
        }

        objectKey = StringUtils.trimLeadingCharacter(objectKey, '/');

        if (!objectKey.startsWith(NOTICE_PREFIX)
                || objectKey.length() == NOTICE_PREFIX.length()) {
            throw new IllegalArgumentException("유효하지 않은 공지 첨부파일 경로입니다.");
        }

        return objectKey;
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        String extension = StringUtils.getFilenameExtension(
                file.getOriginalFilename()
        );
        if (extension == null
                || !ALLOWED_EXTENSIONS.contains(
                        extension.toLowerCase(Locale.ROOT)
                )) {
            throw new IllegalArgumentException(
                    "허용되지 않는 파일 확장자입니다. (pdf, png, jpg, jpeg, docx만 가능)"
            );
        }

        String mimeType = file.getContentType();
        if (mimeType == null
                || !ALLOWED_MIME_TYPES.contains(
                        mimeType.toLowerCase(Locale.ROOT)
                )) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식(MIME Type)입니다.");
        }
    }
}
