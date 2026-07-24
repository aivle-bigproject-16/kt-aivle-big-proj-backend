package com.aivle.big_project.api.domain.cell.service;

import com.aivle.big_project.api.domain.cell.dto.BatteryCellDetailResponse;
import com.aivle.big_project.api.domain.cell.dto.BatteryCellListResponse;
import com.aivle.big_project.api.global.response.PagedResponse;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.cell.BatteryCellWithLatestInspectionProjection;
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

    public PagedResponse<BatteryCellListResponse> getBatteryCells(Pageable pageable) {
        Page<BatteryCellWithLatestInspectionProjection> cells = batteryCellRepository.findBatteryCellsWithLatestInspection(pageable);
        Page<BatteryCellListResponse> responsePage = cells.map(proj -> new BatteryCellListResponse(
                proj.getInspectionId(),
                proj.getBatteryCellId(),
                proj.getCellSerialNo(),
                proj.getModelName(),
                proj.getCellType(),
                proj.getLatestFinalLabel(),
                proj.getLatestAnalyzedAt()
        ));
        return PagedResponse.from(responsePage);
    }

    public BatteryCellDetailResponse getBatteryCellDetail(Long id) {
        BatteryCell cell = batteryCellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배터리 셀입니다."));
        return BatteryCellDetailResponse.from(cell);
    }
}
