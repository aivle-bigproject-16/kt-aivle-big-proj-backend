package com.aivle.big_project.domain.simulation;

import com.aivle.big_project.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    // DB: batch_count
    // 서버가 batchSize, batteryCellCount로 계산
    @Column(name = "batch_count", nullable = false)
    private Integer batchCount;

    // DTO: batchSize
    // DB: cells_per_batch
    @Column(name = "cells_per_batch", nullable = false)
    private Integer batchSize;

    // DTO: batteryCellCount
    // DB: battery_cell_count (새 컬럼)
    @Column(name = "battery_cell_count", nullable = false)
    private Integer batteryCellCount;

    // DTO: captureSpeed
    // DB: interval_ms
    @Column(name = "interval_ms", nullable = false)
    private Integer captureSpeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SimulationStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public static SimulationRun start(
            User requestedBy,
            int batchSize,
            int batteryCellCount,
            int captureSpeed
    ) {
        SimulationRun simulationRun = new SimulationRun();

        simulationRun.requestedBy = requestedBy;
        simulationRun.batchSize = batchSize;
        simulationRun.batteryCellCount = batteryCellCount;
        simulationRun.captureSpeed = captureSpeed;
        simulationRun.batchCount =
                (batteryCellCount + batchSize - 1) / batchSize;
        simulationRun.status = SimulationStatus.RUNNING;

        return simulationRun;
    }

    public void complete() {
        this.status = SimulationStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }
}