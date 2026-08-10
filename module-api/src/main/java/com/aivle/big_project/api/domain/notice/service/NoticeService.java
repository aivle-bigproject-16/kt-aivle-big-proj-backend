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
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    @Transactional
    public NoticeDto.Response createNotice(String email, NoticeDto.Request request) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .author(author)
                .build();
                
        return NoticeDto.Response.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeDto.Response updateNotice(Long noticeId, NoticeDto.Request request) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
                
        notice.update(request.title(), request.content());
        return NoticeDto.Response.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        if (!noticeRepository.existsById(noticeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
        noticeRepository.deleteById(noticeId);
    }

    public PagedResponse<NoticeDto.ListResponse> getNoticeList(Pageable pageable) {
        Page<NoticeDto.ListResponse> responsePage = noticeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(NoticeDto.ListResponse::from);
        return PagedResponse.from(responsePage);
    }

    public NoticeDto.Response getNoticeDetail(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .map(NoticeDto.Response::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
    }
}
