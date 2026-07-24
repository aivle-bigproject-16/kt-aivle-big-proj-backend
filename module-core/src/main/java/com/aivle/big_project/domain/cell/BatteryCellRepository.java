package com.aivle.big_project.domain.cell;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface BatteryCellRepository extends JpaRepository<BatteryCell, Long> {
    Optional<BatteryCell> findByCellSerialNo(String cellSerialNo);

    @Query(value = "SELECT i.id AS inspectionId, b.id AS batteryCellId, b.cellSerialNo AS cellSerialNo, " +
                   "b.modelName AS modelName, b.cellType AS cellType, i.finalLabel AS latestFinalLabel, i.analyzedAt AS latestAnalyzedAt " +
                   "FROM BatteryCell b " +
                   "LEFT JOIN Inspection i ON i.batteryCell = b " +
                   "AND i.id = (SELECT MAX(i2.id) FROM Inspection i2 WHERE i2.batteryCell = b)",
           countQuery = "SELECT COUNT(b) FROM BatteryCell b")
    Page<BatteryCellWithLatestInspectionProjection> findBatteryCellsWithLatestInspection(Pageable pageable);
}
