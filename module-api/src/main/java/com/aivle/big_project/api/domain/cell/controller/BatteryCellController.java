package com.aivle.big_project.api.domain.cell.controller;

import com.aivle.big_project.api.domain.cell.dto.BatteryCellDetailResponse;
import com.aivle.big_project.api.domain.cell.dto.BatteryCellListResponse;
import com.aivle.big_project.api.domain.cell.service.BatteryCellService;
import com.aivle.big_project.api.global.response.ApiResponse;
import com.aivle.big_project.api.global.response.PagedResponse;
import org.springdoc.core.annotations.ParameterObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/battery")
@RequiredArgsConstructor
@Slf4j
public class BatteryCellController {

    private final BatteryCellService batteryCellService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<BatteryCellListResponse>>> getBatteryCells(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String finalLabel,
            @org.springframework.data.web.PageableDefault(sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        try {
            PagedResponse<BatteryCellListResponse> response = batteryCellService.getBatteryCells(keyword, finalLabel, pageable);
            return ResponseEntity.ok(ApiResponse.success("배터리 목록 조회가 완료되었습니다.", response));
        } catch (Exception e) {
            log.error("배터리 목록 조회 실패", e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BatteryCellDetailResponse>> getBatteryCellDetail(@PathVariable Long id) {
        BatteryCellDetailResponse response = batteryCellService.getBatteryCellDetail(id);
        return ResponseEntity.ok(ApiResponse.success("배터리 상세 조회가 완료되었습니다.", response));
    }
}
