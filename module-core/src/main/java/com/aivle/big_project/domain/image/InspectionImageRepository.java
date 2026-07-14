package com.aivle.big_project.domain.image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InspectionImageRepository extends JpaRepository<InspectionImage, Long> {
}
