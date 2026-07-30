package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportsDailyRepository extends JpaRepository<ReportsDaily, Long> {
    Optional<ReportsDaily> findByReportDate(LocalDate reportDate);
    
    @Query("SELECT r FROM ReportsDaily r WHERE r.status = 'PENDING' AND (r.dispatchedAt IS NULL OR r.dispatchedAt < :threshold)")
    List<ReportsDaily> findPendingReportsOlderThan(@Param("threshold") LocalDateTime threshold);
}
