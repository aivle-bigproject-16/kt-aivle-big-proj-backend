package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportsDailyRepository extends JpaRepository<ReportsDaily, Long>, JpaSpecificationExecutor<ReportsDaily> {
    Optional<ReportsDaily> findByReportDate(LocalDate reportDate);
    
    @Query("SELECT r FROM ReportsDaily r WHERE r.status = 'PENDING' AND ((r.dispatchedAt IS NULL AND r.createdAt < :threshold) OR r.dispatchedAt < :threshold)")
    List<ReportsDaily> findPendingReportsOlderThan(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE ReportsDaily r
               SET r.dispatchedAt = :dispatchedAt
             WHERE r.id = :id
               AND r.status = 'PENDING'
               AND (r.dispatchedAt IS NULL OR r.dispatchedAt < :staleBefore)
            """)
    int claimForGeneration(
            @Param("id") Long id,
            @Param("dispatchedAt") LocalDateTime dispatchedAt,
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
