package com.aivle.big_project.domain.cell;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "battery_cell")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatteryCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cell_serial_no", length = 100, nullable = false, unique = true)
    private String cellSerialNo;

    @Column(name = "purchase_id", length = 100)
    private String purchaseId;

    @Column(name = "product_id", length = 100)
    private String productId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "cell_type", length = 50)
    private String cellType;

    @Column(name = "manufactured_date")
    private LocalDate manufacturedDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cell_size", columnDefinition = "jsonb")
    private String cellSize;

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
}
