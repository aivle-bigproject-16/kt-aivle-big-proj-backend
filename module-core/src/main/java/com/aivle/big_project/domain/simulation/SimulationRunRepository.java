package com.aivle.big_project.domain.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {
}
