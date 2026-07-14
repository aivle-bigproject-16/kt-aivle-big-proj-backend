package com.aivle.big_project.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.aivle.big_project")
@EntityScan(basePackages = "com.aivle.big_project")
@EnableJpaRepositories(basePackages = "com.aivle.big_project")
public class LlmApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlmApplication.class, args);
    }
}
