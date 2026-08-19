package com.aivle.big_project.domain.cell;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class BatteryCellRepositoryCustomImpl implements BatteryCellRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Page<BatteryCell> findWithFilters(String keyword, String finalLabel, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
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
                WHERE i.inspection_batch_id IS NOT NULL
                GROUP BY i.battery_cell_id, i.inspection_batch_id
                HAVING COUNT(*) = COUNT(i.final_label)
            ), ranked AS (
                SELECT
                    bs.*,
                    ROW_NUMBER() OVER (
                        PARTITION BY bs.battery_cell_id
                        ORDER BY bs.analyzed_at DESC NULLS LAST, bs.inspection_batch_id DESC
                    ) AS row_number
                FROM batch_summary bs
            ), latest_inspection AS (
                SELECT * FROM ranked WHERE row_number = 1
            )
            """);

        StringBuilder selectSql = new StringBuilder(sql).append("SELECT bc.* FROM battery_cell bc LEFT JOIN latest_inspection li ON bc.id = li.battery_cell_id WHERE 1=1 ");
        StringBuilder countSql = new StringBuilder(sql).append("SELECT COUNT(bc.id) FROM battery_cell bc LEFT JOIN latest_inspection li ON bc.id = li.battery_cell_id WHERE 1=1 ");

        if (keyword != null && !keyword.isBlank()) {
            String condition = " AND bc.cell_serial_no LIKE :keyword ";
            selectSql.append(condition);
            countSql.append(condition);
        }

        if (finalLabel != null && !finalLabel.isBlank()) {
            String condition = " AND li.final_label = :finalLabel ";
            selectSql.append(condition);
            countSql.append(condition);
        }
        
        // Add sorting (simple, id DESC is default)
        selectSql.append(" ORDER BY bc.id DESC ");

        Query selectQuery = entityManager.createNativeQuery(selectSql.toString(), BatteryCell.class);
        Query countQuery = entityManager.createNativeQuery(countSql.toString());

        if (keyword != null && !keyword.isBlank()) {
            selectQuery.setParameter("keyword", "%" + keyword + "%");
            countQuery.setParameter("keyword", "%" + keyword + "%");
        }
        if (finalLabel != null && !finalLabel.isBlank()) {
            selectQuery.setParameter("finalLabel", finalLabel);
            countQuery.setParameter("finalLabel", finalLabel);
        }

        selectQuery.setFirstResult((int) pageable.getOffset());
        selectQuery.setMaxResults(pageable.getPageSize());

        List<BatteryCell> content = selectQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }
}
