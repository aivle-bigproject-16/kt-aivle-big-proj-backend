package com.aivle.big_project.api.domain.cell.controller;

import com.aivle.big_project.api.domain.cell.dto.BatteryCellDetailResponse;
import com.aivle.big_project.api.domain.cell.dto.BatteryCellListResponse;
import com.aivle.big_project.api.domain.cell.service.BatteryCellService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cells")
@RequiredArgsConstructor
@Slf4j
public class BatteryCellController {

    private final BatteryCellService batteryCellService;

    @GetMapping
    public ResponseEntity<Page<BatteryCellListResponse>> getBatteryCells(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("배터리 목록 조회 요청 - Pageable: {}", pageable);
        try {
            Page<BatteryCellListResponse> response = batteryCellService.getBatteryCells(pageable);
            log.info("배터리 목록 조회 성공 - 반환된 데이터 개수: {}", response.getNumberOfElements());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("배터리 목록 조회 실패", e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatteryCellDetailResponse> getBatteryCellDetail(@PathVariable Long id) {
        BatteryCellDetailResponse response = batteryCellService.getBatteryCellDetail(id);
        return ResponseEntity.ok(response);
    }
}
