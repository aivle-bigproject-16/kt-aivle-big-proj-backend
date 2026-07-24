package com.aivle.big_project.api.global.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        PageableData pageable
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                new PageableData(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }

    public record PageableData(
            int page,
            int pageSize,
            long totalElements,
            int totalPages
    ) {}
}
