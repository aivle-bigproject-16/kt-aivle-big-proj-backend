package com.aivle.big_project.api.domain.cell.service;

import com.aivle.big_project.api.domain.cell.dto.BatteryCellDetailResponse;
import com.aivle.big_project.api.domain.cell.dto.BatteryCellListResponse;
import com.aivle.big_project.api.global.response.PagedResponse;
import com.aivle.big_project.domain.cell.BatteryCell;
import com.aivle.big_project.domain.cell.BatteryCellRepository;
import com.aivle.big_project.domain.cell.BatteryCellWithLatestInspectionProjection;
import com.aivle.big_project.domain.defect.DefectResult;
import com.aivle.big_project.domain.defect.DefectResultRepository;
import com.aivle.big_project.domain.image.InspectionImage;
import com.aivle.big_project.domain.image.InspectionImageRepository;
import com.aivle.big_project.domain.inspection.FinalLabel;
import com.aivle.big_project.domain.inspection.Inspection;
import com.aivle.big_project.domain.inspection.InspectionRepository;
import com.aivle.big_project.domain.report.ReportsIndividual;
import com.aivle.big_project.domain.report.ReportsIndividualRepository;
import com.aivle.big_project.api.global.storage.S3ImageUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatteryCellService {

    private final BatteryCellRepository batteryCellRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionImageRepository inspectionImageRepository;
    private final DefectResultRepository defectResultRepository;
    private final ReportsIndividualRepository reportsIndividualRepository;
    private final S3ImageUrlService s3ImageUrlService;

    public PagedResponse<BatteryCellListResponse> getBatteryCells(String keyword, String finalLabel, Pageable pageable) {
        Page<BatteryCell> cells = batteryCellRepository.findWithFilters(keyword, finalLabel, pageable);
        List<Long> batteryCellIds = cells.stream()
                .map(BatteryCell::getId)
                .toList();

        Map<Long, BatteryCellWithLatestInspectionProjection> latestSummaryByCell =
                batteryCellIds.isEmpty()
                        ? Map.of()
                        : batteryCellRepository
                                .findLatestInspectionSummaryByBatteryCellIds(batteryCellIds)
                                .stream()
                                .collect(Collectors.toMap(
                                        BatteryCellWithLatestInspectionProjection::getBatteryCellId,
                                        projection -> projection
                                ));

        Page<BatteryCellListResponse> responsePage = cells.map(cell -> {
            BatteryCellWithLatestInspectionProjection summary = latestSummaryByCell.get(cell.getId());

            return new BatteryCellListResponse(
                    summary == null ? null : summary.getInspectionId(),
                    summary == null ? null : summary.getBatchId(),
                    cell.getId(),
                    cell.getCellSerialNo(),
                    cell.getModelName(),
                    cell.getCellType(),
                    summary == null ? null : FinalLabel.valueOf(summary.getLatestFinalLabel()),
                    summary == null ? null : summary.getLatestAnalyzedAt()
            );
        });
        return PagedResponse.from(responsePage);
    }

    public BatteryCellDetailResponse getBatteryCellDetail(Long id) {
        BatteryCell cell = batteryCellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배터리 셀입니다."));

        List<ReportsIndividual> reports = reportsIndividualRepository.findByBatteryCellId(cell.getId());
        List<BatteryCellDetailResponse.ReportDto> reportDtos = reports.stream().map(rep -> BatteryCellDetailResponse.ReportDto.builder()
                .reportId(rep.getId())
                .inspectionId(rep.getRepresentativeInspection() != null ? rep.getRepresentativeInspection().getId() : null)
                .status(rep.getStatus().name())
                .title(rep.getTitle())
                .createdAt(rep.getCreatedAt())
                .updatedAt(rep.getUpdatedAt())
                .build()).toList();

        List<Inspection> inspections = inspectionRepository.findAllByBatteryCellIdOrderByBatchDesc(id);

        Map<Long, List<Inspection>> inspectionsByBatch = inspections.stream()
                .collect(Collectors.groupingBy(
                        inspection -> inspection.getInspectionBatch().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, List<Inspection>> completedInspectionsByBatch = inspectionsByBatch.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .allMatch(inspection -> inspection.getFinalLabel() != null))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        if (completedInspectionsByBatch.isEmpty()) {
            return BatteryCellDetailResponse.builder()
                    .batteryCellId(cell.getId())
                    .cellSerialNo(cell.getCellSerialNo())
                    .purchaseId(cell.getPurchaseId())
                    .productId(cell.getProductId())
                    .modelName(cell.getModelName())
                    .cellType(cell.getCellType())
                    .manufacturedDate(cell.getManufacturedDate())
                    .createdAt(cell.getCreatedAt())
                    .updatedAt(cell.getUpdatedAt())
                    .inspections(List.of())
                    .reports(reportDtos)
                    .build();
        }

        List<Long> inspectionIds = completedInspectionsByBatch.values().stream()
                .flatMap(List::stream)
                .map(Inspection::getId)
                .toList();

        Map<Long, List<InspectionImage>> imagesByInspection = inspectionImageRepository.findByInspectionIdIn(inspectionIds)
                .stream().collect(Collectors.groupingBy(img -> img.getInspection().getId()));

        Map<Long, List<DefectResult>> defectsByInspection = defectResultRepository.findByInspectionIdIn(inspectionIds)
                .stream().collect(Collectors.groupingBy(def -> def.getInspection().getId()));

        List<BatteryCellDetailResponse.InspectionDto> inspectionDtos =
                completedInspectionsByBatch.entrySet().stream()
                        .map(entry -> toInspectionDto(
                                entry.getKey(),
                                entry.getValue(),
                                imagesByInspection,
                                defectsByInspection
                        ))
                        .toList();

        return BatteryCellDetailResponse.builder()
                .batteryCellId(cell.getId())
                .cellSerialNo(cell.getCellSerialNo())
                .purchaseId(cell.getPurchaseId())
                .productId(cell.getProductId())
                .modelName(cell.getModelName())
                .cellType(cell.getCellType())
                .manufacturedDate(cell.getManufacturedDate())
                .createdAt(cell.getCreatedAt())
                .updatedAt(cell.getUpdatedAt())
                .inspections(inspectionDtos)
                .reports(reportDtos)
                .build();
    }

    private BatteryCellDetailResponse.InspectionDto toInspectionDto(
            Long batchId,
            List<Inspection> inspections,
            Map<Long, List<InspectionImage>> imagesByInspection,
            Map<Long, List<DefectResult>> defectsByInspection
    ) {
        List<Long> inspectionIds = inspections.stream()
                .map(Inspection::getId)
                .toList();

        List<BatteryCellDetailResponse.InspectionImageDto> imageDtos = inspections.stream()
                .flatMap(inspection -> imagesByInspection
                        .getOrDefault(inspection.getId(), List.of())
                        .stream()
                        .map(image -> BatteryCellDetailResponse.InspectionImageDto.builder()
                                .imageId(image.getId())
                                .inspectionId(inspection.getId())
                                .inspectionType(inspection.getInspectionType())
                                .imageType(image.getImageType())
                                .imageUrl(
                                        s3ImageUrlService.createGetUrl(
                                                image.getBucketName(),
                                                image.getObjectKey()
                                        )
                                )
                                .build()))
                .toList();

        List<BatteryCellDetailResponse.DefectResultDto> defectDtos = inspections.stream()
                .flatMap(inspection -> defectsByInspection
                        .getOrDefault(inspection.getId(), List.of())
                        .stream()
                        .map(defect -> BatteryCellDetailResponse.DefectResultDto.builder()
                                .defectResultId(defect.getId())
                                .inspectionId(inspection.getId())
                                .attemptNo(defect.getAttemptNo())
                                .label(defect.getLabel())
                                .imageId(defect.getInspectionImage() != null
                                        ? defect.getInspectionImage().getId()
                                        : null)
                                .imageType(defect.getImageType())
                                .defectType(defect.getDefectType())
                                .imageUrl(
                                        defect.getInspectionImage() == null
                                                ? null
                                                : s3ImageUrlService.createGetUrl(
                                                defect.getInspectionImage().getBucketName(),
                                                defect.getInspectionImage().getObjectKey()
                                        )
                                )
                                .confidence(defect.getConfidence())
                                .bbox(defect.getBbox())
                                .build()))
                .toList();

        return BatteryCellDetailResponse.InspectionDto.builder()
                .batchId(batchId)
                .inspectionIds(inspectionIds)
                .finalLabel(aggregateFinalLabel(inspections))
                .analyzedAt(findLatestAnalyzedAt(inspections))
                .images(imageDtos)
                .defectResults(defectDtos)
                .build();
    }

    private FinalLabel aggregateFinalLabel(List<Inspection> inspections) {
        if (inspections.stream()
                .anyMatch(inspection -> inspection.getFinalLabel() == FinalLabel.FAIL)) {
            return FinalLabel.FAIL;
        }

        if (inspections.stream()
                .anyMatch(inspection -> inspection.getFinalLabel() == FinalLabel.REJECT)) {
            return FinalLabel.REJECT;
        }

        return FinalLabel.PASS;
    }

    private LocalDateTime findLatestAnalyzedAt(List<Inspection> inspections) {
        return inspections.stream()
                .map(Inspection::getAnalyzedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
