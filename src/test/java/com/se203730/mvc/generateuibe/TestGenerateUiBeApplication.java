package com.se203730.mvc.generateuibe;

import org.springframework.boot.SpringApplication;

public class TestGenerateUiBeApplication {

	public static void main(String[] args) {
		SpringApplication.from(GenerateUiBeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
