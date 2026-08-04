package com.aivle.big_project.domain.inspection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.aivle.big_project.domain.inspection.FinalLabel;

import java.time.LocalDate;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByBatteryCellIdAndFinalLabelIn(Long batteryCellId, List<FinalLabel> labels);
    
    // 3. 배터리 셀 ID와 특정 라벨로 가장 최근 검사 조회 (LLM 개별 리포트용)
    java.util.Optional<Inspection> findTopByBatteryCellIdAndFinalLabelOrderByCreatedAtDesc(Long batteryCellId, FinalLabel finalLabel);
    
    @Query(value = "SELECT COUNT(*) FROM inspection WHERE CAST(created_at AS date) = :date", nativeQuery = true)
    int countTotalInspectedByDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(i) FROM Inspection i WHERE DATE(i.createdAt) = :date AND i.finalLabel = :label")
    int countByFinalLabelAndDate(@Param("date") LocalDate date, @Param("label") FinalLabel label);
    
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

    //특정 배치에 속한 검사들을 ID 오름차순으로 가져와라
    List<Inspection> findByInspectionBatchIdOrderByIdAsc(Long inspectionBatchId);
}
