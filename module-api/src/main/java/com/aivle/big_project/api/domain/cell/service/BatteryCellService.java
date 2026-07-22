package com.aivle.big_project.api.domain.cell.service;

import com.aivle.big_project.api.domain.cell.dto.BatteryCellDetailResponse;
import com.aivle.big_project.api.domain.cell.dto.BatteryCellListResponse;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatteryCellService {

    private final BatteryCellRepository batteryCellRepository;

    public Page<BatteryCellListResponse> getBatteryCells(Pageable pageable) {
        Page<BatteryCell> cells = batteryCellRepository.findAll(pageable);
        return cells.map(BatteryCellListResponse::from);
    }

    public BatteryCellDetailResponse getBatteryCellDetail(Long id) {
        BatteryCell cell = batteryCellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배터리 셀입니다."));
        return BatteryCellDetailResponse.from(cell);
    }
}
