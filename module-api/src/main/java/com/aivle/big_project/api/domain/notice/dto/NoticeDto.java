package com.aivle.big_project.api.domain.notice.dto;

import com.aivle.big_project.domain.notice.Notice;
import java.time.LocalDateTime;

public class NoticeDto {

    public record Request(
            String title,
            String content
    ) {}

    public record Response(
            Long id,
            String title,
            String content,
            String authorName,
            String authorEmail,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(Notice notice) {
            return new Response(
                    notice.getId(),
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getAuthor().getName(),
                    notice.getAuthor().getEmail(),
                    notice.getCreatedAt(),
                    notice.getUpdatedAt()
            );
        }
    }
    
    public record ListResponse(
            Long id,
            String title,
            String authorName,
            LocalDateTime createdAt
    ) {
        public static ListResponse from(Notice notice) {
            return new ListResponse(
                    notice.getId(),
                    notice.getTitle(),
                    notice.getAuthor().getName(),
                    notice.getCreatedAt()
            );
        }
    }
}
