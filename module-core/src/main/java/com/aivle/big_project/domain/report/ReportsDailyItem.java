package com.aivle.big_project.domain.report;

import com.aivle.big_project.domain.inspection.Inspection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports_daily_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportsDailyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_report_id", nullable = false)
    private ReportsDaily reportsDaily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;
}
