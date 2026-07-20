package com.aivle.big_project.domain.inspection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    @Query(value = """
        SELECT i.created_at::date AS label, COUNT(*) AS value
        FROM inspection i
        WHERE i.final_label = 'REJECT'
        AND i.created_at::date BETWEEN :startDate AND :todayDate
        GROUP BY i.created_at::date
        ORDER BY i.created_at::date
        """, nativeQuery = true)
    List<Object[]> findDailyRejectTrend(
            @Param("startDate") LocalDate startDate,
            @Param("todayDate") LocalDate todayDate
    );
}
