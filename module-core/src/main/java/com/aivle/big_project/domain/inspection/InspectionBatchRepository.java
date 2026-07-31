package com.aivle.big_project.domain.inspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspectionBatchRepository extends JpaRepository<InspectionBatch, Long> {
    //현재 시뮬레이션 배치 중 첫번째(가장 ID가 작은) 배치를 가져와라
    Optional<InspectionBatch> findFirstBySimulationRunIdOrderByIdAsc(Long simulationRunId);
}
