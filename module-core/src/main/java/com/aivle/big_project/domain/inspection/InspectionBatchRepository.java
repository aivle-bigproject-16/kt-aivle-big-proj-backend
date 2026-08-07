package com.aivle.big_project.domain.inspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface InspectionBatchRepository extends JpaRepository<InspectionBatch, Long> {
    //현재 시뮬레이션 배치 중 첫번째(가장 ID가 작은) 배치를 가져와라
    Optional<InspectionBatch> findFirstBySimulationRunIdOrderByIdAsc(Long simulationRunId);

    //모든 배치의 id를 순서대로 가져와라
    List<InspectionBatch> findBySimulationRunIdOrderByIdAsc(
            Long simulationRunId
    );
}
