package com.aivle.big_project.domain.inspection;

import com.aivle.big_project.domain.cell.BatteryCell;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "point_groups", columnDefinition = "jsonb")
    private String pointGroups;

    @Column(name = "ai_request_id", length = 100, unique = true)
    private String aiRequestId;

    @Column(name = "inspection_type", length = 20)
    private String inspectionType;

    @Column(name = "ctPorosityRatioMean", length = 20)
    private String ctPorosityRatioMean;

    @Column(name = "ctPorosityRatioMax", length = 20)
    private String ctPorosityRatioMax;

    @Column(name = "ctMaxPoreRatio", length = 20)
    private String ctMaxPoreRatio;

    @Column(name = "ctPoreCount", length = 20)
    private String ctPoreCount;

    @Column(name = "ctSliceCount", length = 20)
    private String ctSliceCount;

    @Column(name = "rgbProsityRatio", length = 20)
    private String rgbProsityRatio;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", length = 20, nullable = false)
    private InspectionType inspectionType;

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
            BatteryCell batteryCell,
            InspectionType inspectionType
    ) {
        Inspection inspection = new Inspection();

        inspection.inspectionBatch = inspectionBatch;
        inspection.batteryCell = batteryCell;
        inspection.inspectionType = inspectionType;
        inspection.status = InspectionStatus.PENDING;

        return inspection;
    }

    public void startCapture() {
        this.status = InspectionStatus.CAPTURING;
    }

    public void completeCapture() {
        this.status = InspectionStatus.CAPTURED;
    }

    public void startAnalysis(String aiRequestId) {
        this.status = InspectionStatus.ANALYZING;
        this.aiRequestId = aiRequestId;
    }
    public void completeAnalysis(
            InspectionStatus status,
            FinalLabel finalLabel,
            String failureReason
    ) {
        this.status = status;
        this.finalLabel = finalLabel;
        this.failureReason = failureReason;
        this.analyzedAt = LocalDateTime.now();
    }

    public void updateSeverity(String ctPorosityRatioMean, String ctPorosityRatioMax, String ctPoreCount, String ctSliceCount, String rgbProsityRatio) {
        this.ctPorosityRatioMean = ctPorosityRatioMean;
        this.ctPorosityRatioMax = ctPorosityRatioMax;
        this.ctPoreCount = ctPoreCount;
        this.ctSliceCount = ctSliceCount;
        this.rgbProsityRatio = rgbProsityRatio;
    }
}
