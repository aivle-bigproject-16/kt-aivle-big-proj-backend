package com.aivle.big_project.domain.inspection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aivle.big_project.domain.inspection.FinalLabel;

import java.time.LocalDate;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByBatteryCellIdAndFinalLabelIn(Long batteryCellId, List<FinalLabel> labels);
    
    // 추가: 특정 배터리 셀의 가장 최신 특정 판정(REJECT) 검사 1건 조회
    java.util.Optional<Inspection> findTopByBatteryCellIdAndFinalLabelOrderByCreatedAtDesc(Long batteryCellId, String finalLabel);
    
    @Query(value = """
        SELECT label, value
        FROM (
            SELECT i.created_at::date AS label, COUNT(*) AS value
            FROM inspection i
            WHERE i.final_label = 'REJECT'
              AND i.created_at::date BETWEEN :startDate AND :todayDate
            GROUP BY i.created_at::date
            ORDER BY i.created_at::date DESC
            LIMIT :size
        ) latest_trend
        ORDER BY label ASC
        """, nativeQuery = true)
    List<Object[]> findDailyRejectTrend(
            @Param("startDate") LocalDate startDate,
            @Param("todayDate") LocalDate todayDate,
            @Param("size") int size
    );

    @Query(value = """
        SELECT COALESCE(bc.purchase_id, 'UNKNOWN') AS label, COUNT(*) AS value
        FROM inspection i
        JOIN battery_cell bc ON bc.id = i.battery_cell_id
        WHERE i.final_label = 'REJECT'
          AND i.created_at::date BETWEEN :startDate AND :todayDate
        GROUP BY COALESCE(bc.purchase_id, 'UNKNOWN')
        ORDER BY value DESC, label ASC
        LIMIT :size
        """, nativeQuery = true)
    List<Object[]> findManufacturerRejectCounts(
            @Param("startDate") LocalDate startDate,
            @Param("todayDate") LocalDate todayDate,
            @Param("size") int size
    );
}
