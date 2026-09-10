package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RagAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagAppApplication.class, args);
	}

}
