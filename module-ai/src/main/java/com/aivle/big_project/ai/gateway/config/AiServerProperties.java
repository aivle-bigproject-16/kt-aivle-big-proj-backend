package com.aivle.big_project.ai.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-server")
public record AiServerProperties(
        String baseUrl,
        String internalApiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
}