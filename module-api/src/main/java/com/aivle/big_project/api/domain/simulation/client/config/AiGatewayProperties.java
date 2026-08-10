package com.aivle.big_project.api.domain.simulation.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-gateway")
public record AiGatewayProperties(
        String baseUrl,
        String internalApiKey,
        Duration connectTimeout,
        Duration readTimeout,
        String callbackUrl
) {
}