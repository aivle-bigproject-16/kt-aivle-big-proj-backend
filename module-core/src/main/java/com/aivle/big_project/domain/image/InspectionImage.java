package com.aivle.big_project.domain.image;

import com.aivle.big_project.domain.inspection.Inspection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inspection_image",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inspection_image_object",
                        columnNames = {"inspection_id", "bucket_name", "object_key"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "image_type", length = 20, nullable = false)
    private String imageType;

    @Column(name = "bucket_name", length = 100, nullable = false)
    private String bucketName;

    @Column(name = "object_key", length = 500, nullable = false)
    private String objectKey;

    @Column(name = "source_object_key", length = 500)
    private String sourceObjectKey;

    @Column(name = "storage_type", length = 30, nullable = false)
    private String storageType;

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

    @Column(name = "volume")
    private Long volume;

    @Column(name = "index")
    private Long index;

    @Column(name = "axis", length = 20)
    private String axis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
