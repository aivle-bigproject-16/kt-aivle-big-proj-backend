package com.aivle.big_project.domain.image;

import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.inspection.Inspection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "battery_cell_image",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_battery_cell_image_object",
                        columnNames = {"battery_cell_id", "bucket_name", "object_key"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatteryCellImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battery_cell_id", nullable = false)
    private BatteryCell batteryCell;

    @Column(name = "image_type", length = 20, nullable = false)
    private String imageType; // CT 또는 RGB

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_set", length = 20, nullable = false)
    private CaptureSet captureSet = CaptureSet.INITIAL;

    @Column(name = "bucket_name", length = 100, nullable = false)
    private String bucketName;

    @Column(name = "object_key", length = 500, nullable = false)
    private String objectKey;

    @Column(name = "storage_type", length = 30, nullable = false)
    private String storageType; // S3

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static BatteryCellImage create(
            BatteryCell batteryCell,
            String imageType,
            String bucketName,
            String objectKey,
            String storageType
    ) {
        BatteryCellImage image = new BatteryCellImage();

        image.batteryCell = batteryCell;
        image.imageType = imageType;
        image.bucketName = bucketName;
        image.objectKey = objectKey;
        image.storageType = storageType;

        return image;
    }
}
