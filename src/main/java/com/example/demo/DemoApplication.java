package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates the @Scheduled annotation used in TokenBlocklistService.purgeExpired().
// @EnableJpaAuditing must live on the main application class so that JPA auto-configuration
// is fully initialized before the auditing infrastructure is registered.  A separate
// @Configuration class with @EnableJpaAuditing is processed before auto-configuration
// beans are ready, which causes Spring Boot 4.x to fail with "entityManagerFactory not found".
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
