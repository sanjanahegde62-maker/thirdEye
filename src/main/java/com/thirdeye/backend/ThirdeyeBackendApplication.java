package com.thirdeye.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ThirdeyeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThirdeyeBackendApplication.class, args);
	}
}
