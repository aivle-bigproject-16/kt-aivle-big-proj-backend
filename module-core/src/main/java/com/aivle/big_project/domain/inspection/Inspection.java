package com.aivle.big_project.domain.inspection;

import com.aivle.big_project.domain.cell.BatteryCell;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "inspection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_batch_id", nullable = false)
    private InspectionBatch inspectionBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battery_cell_id", nullable = false)
    private BatteryCell batteryCell;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private InspectionStatus status = InspectionStatus.PENDING;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_label", length = 20)
    private FinalLabel finalLabel;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

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

    public static Inspection create(
            InspectionBatch inspectionBatch,
            BatteryCell batteryCell
    ) {
        Inspection inspection = new Inspection();

        inspection.inspectionBatch = inspectionBatch;
        inspection.batteryCell = batteryCell;
        inspection.status = InspectionStatus.PENDING;

        return inspection;
    }

    public void startCapture() {
        this.status = InspectionStatus.CAPTURING;
    }

    public void completeCapture() {
        this.status = InspectionStatus.CAPTURED;
    }

    public void startAnalysis() {
        this.status = InspectionStatus.ANALYZING;
    }
}
