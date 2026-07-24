package com.aivle.big_project.api.domain.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
