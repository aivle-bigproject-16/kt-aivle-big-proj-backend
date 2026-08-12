package com.aivle.big_project.domain.defect;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DefectResultRepository extends JpaRepository<DefectResult, Long> {

    boolean existsByAiRequestId(String aiRequestId);

    List<DefectResult> findByInspectionIdIn(List<Long> inspectionIds);
    @Query(value = """
        SELECT i.defect_type AS label, COUNT(*) AS value
        FROM defect_result i
        WHERE i.label = 'REJECT'
        AND i.created_at::date BETWEEN :startDate AND :todayDate
        GROUP BY i.defect_type
        ORDER BY i.defect_type
        LIMIT :size
        """, nativeQuery = true)
    List<Object[]> findDefectResultType(
            @Param("startDate") LocalDate startDate,
            @Param("todayDate") LocalDate todayDate,
            @Param("size") int size
    );
}
