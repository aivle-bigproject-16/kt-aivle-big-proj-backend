package com.aivle.big_project.domain.inspection;

import com.aivle.big_project.domain.simulation.SimulationRun;
import com.aivle.big_project.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "inspection_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_run_id")
    private SimulationRun simulationRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private InspectionBatchStatus status;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static InspectionBatch create(
            SimulationRun simulationRun,
            User requestedBy
    ) {
        InspectionBatch inspectionBatch = new InspectionBatch();

        inspectionBatch.simulationRun = simulationRun;
        inspectionBatch.requestedBy = requestedBy;
        inspectionBatch.status = InspectionBatchStatus.REGISTERED;

        return inspectionBatch;
    }

    public void startCapture() {
        this.status = InspectionBatchStatus.CAPTURING;
    }

    public void completeCapture() {
        this.status = InspectionBatchStatus.CAPTURED;
    }

    public void startAnalysis() {
        this.status = InspectionBatchStatus.ANALYZING;
    }

    public void complete() {
        this.status = InspectionBatchStatus.COMPLETED;
    }
}
