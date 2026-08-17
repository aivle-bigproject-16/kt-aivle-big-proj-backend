package com.aivle.big_project.api.domain.simulation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimulationDataResetter {

    private final JdbcTemplate jdbcTemplate;

    public void reset() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE
                reports_daily_item,
                reports_individual,
                reports_daily,
                defect_result,
                inspection_image,
                inspection,
                inspection_batch,
                simulation_run
            RESTART IDENTITY
            """);
    }
}