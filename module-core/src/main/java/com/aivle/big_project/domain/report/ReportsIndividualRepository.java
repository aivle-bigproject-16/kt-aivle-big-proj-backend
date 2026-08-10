package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportsIndividualRepository extends JpaRepository<ReportsIndividual, Long> {
    List<ReportsIndividual> findByBatteryCellId(Long batteryCellId);
    
    @Query("SELECT MAX(r.version) FROM ReportsIndividual r WHERE r.batteryCell.id = :batteryCellId")
    Integer findMaxVersionByBatteryCellId(@Param("batteryCellId") Long batteryCellId);
    
    @Query("SELECT r FROM ReportsIndividual r WHERE r.status = 'PENDING' AND (r.dispatchedAt IS NULL OR r.dispatchedAt < :threshold)")
    List<ReportsIndividual> findPendingReportsOlderThan(@Param("threshold") LocalDateTime threshold);
}
