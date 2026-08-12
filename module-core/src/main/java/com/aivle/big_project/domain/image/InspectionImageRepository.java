package com.aivle.big_project.domain.image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InspectionImageRepository extends JpaRepository<InspectionImage, Long> {
    List<InspectionImage> findByInspectionIdIn(List<Long> inspectionIds);

    boolean existsByInspectionIdAndBatteryCellImageId(
            Long inspectionId,
            Long batteryCellImageId
    );

    boolean existsByInspectionIdAndBatteryCellImageIdAndAttemptNo(
            Long inspectionId,
            Long batteryCellImageId,
            int attemptNo
    );

    List<InspectionImage> findByInspectionIdAndAttemptNoOrderByIdAsc(
            Long inspectionId,
            int attemptNo
    );
}
