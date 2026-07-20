package com.aivle.big_project.api.domain.auth.dto;

import com.aivle.big_project.domain.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private Role role;
}

