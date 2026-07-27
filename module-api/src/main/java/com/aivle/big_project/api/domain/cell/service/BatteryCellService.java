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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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

    public PagedResponse<BatteryCellListResponse> getBatteryCells(Pageable pageable) {
        Page<BatteryCellWithLatestInspectionProjection> cells = batteryCellRepository.findBatteryCellsWithLatestInspection(pageable);
        Page<BatteryCellListResponse> responsePage = cells.map(proj -> new BatteryCellListResponse(
                proj.getInspectionId(),
                proj.getBatteryCellId(),
                proj.getCellSerialNo(),
                proj.getModelName(),
                proj.getCellType(),
                proj.getLatestFinalLabel(),
                proj.getLatestAnalyzedAt()
        ));
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

        List<Inspection> inspections = inspectionRepository.findByBatteryCellIdAndFinalLabelIn(id, List.of(FinalLabel.REJECT, FinalLabel.FAIL));
        if (inspections.isEmpty()) {
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

        List<Long> inspectionIds = inspections.stream().map(Inspection::getId).toList();

        Map<Long, List<InspectionImage>> imagesByInspection = inspectionImageRepository.findByInspectionIdIn(inspectionIds)
                .stream().collect(Collectors.groupingBy(img -> img.getInspection().getId()));

        Map<Long, List<DefectResult>> defectsByInspection = defectResultRepository.findByInspectionIdIn(inspectionIds)
                .stream().collect(Collectors.groupingBy(def -> def.getInspection().getId()));

        List<BatteryCellDetailResponse.InspectionDto> inspectionDtos = inspections.stream().map(ins -> {
            List<BatteryCellDetailResponse.InspectionImageDto> imageDtos = imagesByInspection.getOrDefault(ins.getId(), List.of())
                    .stream().map(img -> BatteryCellDetailResponse.InspectionImageDto.builder()
                            .imageId(img.getId())
                            .imageType(img.getImageType())
                            .imageUrl(img.getObjectKey()) // Using objectKey as URL for now
                            .build()).toList();

            List<BatteryCellDetailResponse.DefectResultDto> defectDtos = defectsByInspection.getOrDefault(ins.getId(), List.of())
                    .stream().map(def -> BatteryCellDetailResponse.DefectResultDto.builder()
                            .defectResultId(def.getId())
                            .label(def.getLabel())
                            .imageId(def.getInspectionImage() != null ? def.getInspectionImage().getId() : null)
                            .imageType(def.getImageType())
                            .defectType(def.getDefectType())
                            .imageUrl(def.getInspectionImage() != null ? def.getInspectionImage().getObjectKey() : null)
                            .confidence(def.getConfidence())
                            .bbox(def.getBbox())
                            .build()).toList();

            return BatteryCellDetailResponse.InspectionDto.builder()
                    .inspectionId(ins.getId())
                    .finalLabel(ins.getFinalLabel())
                    .analyzedAt(ins.getAnalyzedAt())
                    .image(imageDtos)
                    .defectResults(defectDtos)
                    .build();
        }).toList();

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
}
