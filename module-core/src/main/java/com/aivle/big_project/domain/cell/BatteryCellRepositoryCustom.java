package com.aivle.big_project.domain.cell;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BatteryCellRepositoryCustom {
    Page<BatteryCell> findWithFilters(String keyword, String finalLabel, Pageable pageable);
}
