package com.aivle.big_project.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportsIndividualRepository extends JpaRepository<ReportsIndividual, Long> {
    List<ReportsIndividual> findByBatteryCellId(Long batteryCellId);
}
