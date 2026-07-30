package com.aivle.big_project.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.aivle.big_project.ai", "com.aivle.big_project.core"})
@EnableJpaRepositories(basePackages = "com.aivle.big_project.domain")
@EntityScan(basePackages = "com.aivle.big_project.domain")
@EnableAsync
@EnableScheduling
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
