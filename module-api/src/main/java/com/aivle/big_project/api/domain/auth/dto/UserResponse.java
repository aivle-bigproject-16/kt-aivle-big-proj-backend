package com.aivle.big_project.api.domain.auth.dto;

import com.aivle.big_project.domain.user.Role;
import lombok.Builder;

@Builder
public record UserResponse(
        Long id,
        String email,
        String name,
        Role role
) {
}
