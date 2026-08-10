package com.aivle.big_project.domain.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reports_daily")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportsDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReportStatus status;

    @Column(name = "title")
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

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
    
    public void changeStatusToPending() {
        this.status = ReportStatus.PENDING;
        // 상태를 다시 PENDING으로 돌리므로 실패 사유나 결과값들은 일단 초기화할 수도 있지만,
        // 기존 데이터를 덮어쓰기 위해 여기선 status만 변경함
    }
    
    public void markAsDispatched() {
        this.dispatchedAt = LocalDateTime.now();
    }
    
    public void updateResult(ReportStatus status, String title, String content, String failureReason) {
        this.status = status;
        this.title = title;
        this.content = content;
        this.failureReason = failureReason;
    }

    public void updateSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }
}
