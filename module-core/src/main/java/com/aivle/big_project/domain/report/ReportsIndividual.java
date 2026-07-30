package com.aivle.big_project.domain.report;

import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.inspection.Inspection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports_individual")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportsIndividual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battery_cell_id", nullable = false)
    private BatteryCell batteryCell;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_inspection_id")
    private Inspection representativeInspection;

    @Column(name = "source_inspection_ids", columnDefinition = "jsonb")
    private String sourceInspectionIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReportStatus status;

    @Column(name = "title")
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

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
    
    // 워커 스레드에서 생성 시작 시(디스패치) 마킹용 메서드
    public void markAsDispatched() {
        this.dispatchedAt = LocalDateTime.now();
    }
    
    public void updateResult(ReportStatus status, String title, String content, String failureReason) {
        this.status = status;
        this.title = title;
        this.content = content;
        this.failureReason = failureReason;
    }
}
