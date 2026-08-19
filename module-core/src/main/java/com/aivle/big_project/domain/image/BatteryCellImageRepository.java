package com.aivle.big_project.domain.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatteryCellImageRepository
        extends JpaRepository<BatteryCellImage, Long> {

    List<BatteryCellImage> findByBatteryCellIdAndImageTypeAndRecaptureNoOrderByIdAsc(
            Long batteryCellId,
            String imageType,
            int recaptureNo
    );
}
