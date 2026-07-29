package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportsDailyItemRepository extends JpaRepository<ReportsDailyItem, Long> {
    List<ReportsDailyItem> findByReportsDailyId(Long reportsDailyId);
}
