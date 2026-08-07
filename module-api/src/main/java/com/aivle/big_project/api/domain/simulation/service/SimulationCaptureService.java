package com.aivle.big_project.api.domain.simulation.service;

import com.aivle.big_project.domain.image.BatteryCellImage;
import com.aivle.big_project.domain.image.BatteryCellImageRepository;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionBatch;
import com.aivle.big_project.domain.inspection.InspectionBatchRepository;
import com.aivle.big_project.domain.inspection.InspectionBatchStatus;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationCaptureService {

    private final InspectionBatchRepository inspectionBatchRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository inspectionImageRepository;
    private final BatteryCellImageRepository batteryCellImageRepository;

    public void capture(Long batchId) {
        InspectionBatch batch = inspectionBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "검사 배치를 찾을 수 없습니다."
                ));

        if (batch.getStatus() != InspectionBatchStatus.CAPTURING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "촬영 중인 배치만 이미지 생성이 가능합니다."
            );
        }

        List<Inspection> inspections =
                inspectionRepository.findByInspectionBatchIdOrderByIdAsc(batchId);

        for (Inspection inspection : inspections) {
            createInspectionImagesFromSource(inspection);
        }
    }

    private void createInspectionImagesFromSource(Inspection inspection) {
        String imageType = inspection.getInspectionType().name();

        List<BatteryCellImage> sourceImages =
                batteryCellImageRepository
                        .findByBatteryCellIdAndImageTypeOrderByIdAsc(
                                inspection.getBatteryCell().getId(),
                                imageType
                        );

        if (sourceImages.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "%s 원본 이미지가 없습니다. batteryCellId=%d"
                            .formatted(
                                    imageType,
                                    inspection.getBatteryCell().getId()
                            )
            );
        }

        for (BatteryCellImage sourceImage : sourceImages) {
            boolean alreadyLinked =
                    inspectionImageRepository
                            .existsByInspectionIdAndBatteryCellImageId(
                                    inspection.getId(),
                                    sourceImage.getId()
                            );

            if (alreadyLinked) {
                continue;
            }

            InspectionImage inspectionImage =
                    InspectionImage.createFromSource(
                            inspection,
                            sourceImage
                    );

            inspectionImageRepository.save(inspectionImage);
        }
    }

    private String buildObjectKey(
            Long simulationRunId,
            Long batchId,
            Long inspectionId,
            String imageType
    ) {
        return "simulations/%d/batches/%d/inspections/%d/%s.png"
                .formatted(
                        simulationRunId,
                        batchId,
                        inspectionId,
                        imageType.toLowerCase(Locale.ROOT)
                );
    }
}