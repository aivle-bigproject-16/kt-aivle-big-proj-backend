package com.aivle.big_project.api.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;

@Builder
public record ImageMappingDto(
        String imageType,
        Long imageId,
        String imgUrl,
        Long volume,
        Long index,
        String axis,
        Integer imageWidth,
        Integer imageHeight,
        @JsonRawValue String bbox
) {
}
