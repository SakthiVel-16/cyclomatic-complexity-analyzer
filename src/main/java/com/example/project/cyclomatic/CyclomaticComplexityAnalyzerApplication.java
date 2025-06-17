package com.example.project.cyclomatic;

import com.example.project.cyclomatic.service.ComplexityCalculator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class CyclomaticComplexityAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CyclomaticComplexityAnalyzerApplication.class, args);
	}

}