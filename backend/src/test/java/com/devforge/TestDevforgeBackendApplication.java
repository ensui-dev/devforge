package com.devforge;

import org.springframework.boot.SpringApplication;

public class TestDevforgeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(DevforgeBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
