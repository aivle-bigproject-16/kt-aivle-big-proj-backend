package com.aivle.big_project.ai.gateway.config;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AiServerProperties.class)
public class AiServerConfig {

    @Bean
    public WebClient aiServerWebClient(
            AiServerProperties properties
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.connectTimeout().toMillis()
                )
                .responseTimeout(properties.readTimeout());

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .defaultHeader(
                        "X-Internal-Api-Key",
                        properties.internalApiKey()
                )
                .build();
    }
}