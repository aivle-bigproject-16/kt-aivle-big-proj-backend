package com.aivle.big_project.domain.defect;

import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.inspection.Inspection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "defect_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefectResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_image_id")
    private InspectionImage inspectionImage;

    @Column(name = "image_type", length = 20)
    private String imageType;

    @Column(name = "label", length = 20, nullable = false)
    private String label;

    @Column(name = "defect_type", length = 50)
    private String defectType;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "bbox", columnDefinition = "jsonb")
    private String bbox;

    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
