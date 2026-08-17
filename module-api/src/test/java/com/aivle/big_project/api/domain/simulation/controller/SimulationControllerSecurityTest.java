package com.aivle.big_project.api.domain.simulation.controller;

import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.StartRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationControllerSecurityTest {

    @Test
    void destructiveResetRequiresAdminRole() throws NoSuchMethodException {
        PreAuthorize authorization = SimulationController.class
                .getMethod("start", StartRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("!#request.resetBeforeStart() or hasRole('ADMIN')");
    }
}
