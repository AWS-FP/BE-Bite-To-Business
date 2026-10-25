package com.se203730.mvc.generateuibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi chạy Spring Boot. Spring tự quét các controller, service, repository và config bên dưới package này.
 */
@SpringBootApplication
public class GenerateUiBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenerateUiBeApplication.class, args);
    }

}
