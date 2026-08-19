package com.aivle.big_project.api.domain.notice.service;

import com.aivle.big_project.api.domain.notice.dto.NoticeDto;
import com.aivle.big_project.domain.notice.Notice;
import com.aivle.big_project.domain.notice.NoticeRepository;
import com.aivle.big_project.api.global.response.PagedResponse;
import com.aivle.big_project.domain.user.User;
import com.aivle.big_project.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import com.aivle.big_project.api.global.s3.S3FileStorageService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final S3FileStorageService s3FileStorageService;

    @Transactional
    public NoticeDto.Response createNotice(String email, NoticeDto.Request request) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .author(author)
                .build();

        if (request.file() != null && !request.file().isEmpty()) {
            String fileUrl = s3FileStorageService.uploadFile(request.file());
            notice.updateFile(fileUrl, request.file().getOriginalFilename());
            scheduleAttachmentChange(null, fileUrl);
        }
                
        Notice savedNotice = noticeRepository.save(notice);
        return toResponse(savedNotice);
    }

    @Transactional
    public NoticeDto.Response updateNotice(Long noticeId, NoticeDto.Request request) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
                
        notice.update(request.title(), request.content());

        // 새로운 파일이 들어온 경우 교체
        if (request.file() != null && !request.file().isEmpty()) {
            String previousFileReference = notice.getFileUrl();
            String newFileReference = s3FileStorageService.uploadFile(
                    request.file()
            );
            notice.updateFile(
                    newFileReference,
                    request.file().getOriginalFilename()
            );
            scheduleAttachmentChange(
                    previousFileReference,
                    newFileReference
            );
        } 
        // 명시적으로 파일 삭제 요청이 온 경우
        else if (Boolean.TRUE.equals(request.deleteFile())) {
            if (notice.getFileUrl() != null) {
                String previousFileReference = notice.getFileUrl();
                notice.updateFile(null, null);
                scheduleAttachmentChange(previousFileReference, null);
            }
        }
        
        return toResponse(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
        
        if (notice.getFileUrl() != null) {
            scheduleAttachmentChange(notice.getFileUrl(), null);
        }
        
        noticeRepository.deleteById(noticeId);
    }

    public PagedResponse<NoticeDto.ListResponse> getNoticeList(String keyword, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Notice> spec = org.springframework.data.jpa.domain.Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
        }
        
        // Ensure sorting by createdAt DESC if pageable doesn't specify sort, or let controller's PageableDefault handle it.
        // Controller already sets sort = "createdAt", direction = Sort.Direction.DESC
        Page<NoticeDto.ListResponse> responsePage = noticeRepository.findAll(spec, pageable)
                .map(NoticeDto.ListResponse::from);
        return PagedResponse.from(responsePage);
    }

    public NoticeDto.Response getNoticeDetail(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
    }

    private NoticeDto.Response toResponse(Notice notice) {
        return NoticeDto.Response.from(
                notice,
                s3FileStorageService.createDownloadUrl(notice.getFileUrl())
        );
    }

    private void scheduleAttachmentChange(
            String previousFileReference,
            String newFileReference
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (previousFileReference != null) {
                            s3FileStorageService.deleteFile(
                                    previousFileReference
                            );
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED
                                && newFileReference != null) {
                            s3FileStorageService.deleteFile(
                                    newFileReference
                            );
                        }
                    }
                }
        );
    }
}
