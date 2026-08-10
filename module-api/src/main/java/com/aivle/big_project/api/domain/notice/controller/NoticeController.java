package com.aivle.big_project.api.domain.notice.controller;

import com.aivle.big_project.api.domain.notice.dto.NoticeDto;
import com.aivle.big_project.api.domain.notice.service.NoticeService;
import com.aivle.big_project.api.global.response.ApiResponse;
import com.aivle.big_project.api.global.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NoticeDto.Response>> createNotice(
            @RequestBody NoticeDto.Request request,
            @AuthenticationPrincipal UserDetails userDetails) {
        NoticeDto.Response response = noticeService.createNotice(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("공지사항 작성이 완료되었습니다.", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NoticeDto.Response>> updateNotice(
            @PathVariable Long id,
            @RequestBody NoticeDto.Request request) {
        NoticeDto.Response response = noticeService.updateNotice(id, request);
        return ResponseEntity.ok(ApiResponse.success("공지사항 수정이 완료되었습니다.", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.ok(ApiResponse.success("공지사항 삭제가 완료되었습니다.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NoticeDto.ListResponse>>> getNoticeList(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<NoticeDto.ListResponse> response = noticeService.getNoticeList(pageable);
        return ResponseEntity.ok(ApiResponse.success("공지사항 목록 조회가 완료되었습니다.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoticeDto.Response>> getNoticeDetail(@PathVariable Long id) {
        NoticeDto.Response response = noticeService.getNoticeDetail(id);
        return ResponseEntity.ok(ApiResponse.success("공지사항 상세 조회가 완료되었습니다.", response));
    }
}
