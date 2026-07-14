package com.aivle.big_project.domain.cell;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BatteryCellRepository extends JpaRepository<BatteryCell, Long> {
    Optional<BatteryCell> findByCellSerialNo(String cellSerialNo);
}
