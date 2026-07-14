package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface ReportsDailyRepository extends JpaRepository<ReportsDaily, Long> {
    Optional<ReportsDaily> findByReportDate(LocalDate reportDate);
}
