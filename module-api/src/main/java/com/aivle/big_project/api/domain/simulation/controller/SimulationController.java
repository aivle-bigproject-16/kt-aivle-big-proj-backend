package com.aivle.big_project.api.domain.simulation.controller;

import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.SnapshotResponse;
import com.aivle.big_project.api.domain.simulation.dto.SimulationDto.StartRequest;
import com.aivle.big_project.api.domain.simulation.service.SimulationService;
import com.aivle.big_project.api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * POST /sim
     * 시뮬레이션을 시작하고 초기 진행 상태를 반환합니다.
     */
    @PostMapping
    @PreAuthorize("!#request.resetBeforeStart() or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SnapshotResponse>> start(
            @Valid @RequestBody StartRequest request
    ) {
        SnapshotResponse response = simulationService.start(request);

        return ResponseEntity.ok(
                ApiResponse.success("검사를 실행합니다.", response)
        );
    }

    /**
     * GET /sim
     * 프론트 화면 새로고침 시 최신 진행 상태를 복구합니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SnapshotResponse>> getSnapshot() {
        SnapshotResponse response = simulationService.getSnapshot();

        return ResponseEntity.ok(
                ApiResponse.success("검사 진행 상황 복구가 완료되었습니다.", response)
        );
    }
}
