package com.aivle.big_project.domain.cell;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatteryCellRepository extends JpaRepository<BatteryCell, Long> {
    Optional<BatteryCell> findByCellSerialNo(String cellSerialNo);

    List<BatteryCell> findByCellSerialNoStartingWithOrderByCellSerialNoAsc(
            String prefix,
            Pageable pageable
    );

    @Query(value = """
        WITH batch_summary AS (
            SELECT
                i.battery_cell_id,
                i.inspection_batch_id,
                MAX(i.id) AS representative_inspection_id,
                CASE
                    WHEN COUNT(*) FILTER (WHERE i.final_label = 'FAIL') > 0 THEN 'FAIL'
                    WHEN COUNT(*) FILTER (WHERE i.final_label = 'REJECT') > 0 THEN 'REJECT'
                    ELSE 'PASS'
                END AS final_label,
                MAX(i.analyzed_at) AS analyzed_at
            FROM inspection i
            WHERE i.battery_cell_id IN (:batteryCellIds)
              AND i.inspection_batch_id IS NOT NULL
            GROUP BY i.battery_cell_id, i.inspection_batch_id
            HAVING COUNT(*) = COUNT(i.final_label)
        ), ranked AS (
            SELECT
                batch_summary.*,
                ROW_NUMBER() OVER (
                    PARTITION BY battery_cell_id
                    ORDER BY analyzed_at DESC NULLS LAST, inspection_batch_id DESC
                ) AS row_number
            FROM batch_summary
        )
        SELECT
            representative_inspection_id AS "inspectionId",
            battery_cell_id AS "batteryCellId",
            inspection_batch_id AS "batchId",
            final_label AS "latestFinalLabel",
            analyzed_at AS "latestAnalyzedAt"
        FROM ranked
        WHERE row_number = 1
        """, nativeQuery = true)
    List<BatteryCellWithLatestInspectionProjection> findLatestInspectionSummaryByBatteryCellIds(
            @Param("batteryCellIds") List<Long> batteryCellIds
    );
}
