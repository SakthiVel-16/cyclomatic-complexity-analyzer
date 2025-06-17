package com.example.project.cyclomatic.controller;

import com.example.project.cyclomatic.service.ComplexityCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST Controller for exposing the Cyclomatic Complexity Analyzer as a web
 * service.
 * Handles incoming HTTP requests for code analysis.
 */
@RestController // Marks this class as a Spring REST controller
@RequestMapping("/api/complexity") // Base path for all endpoints in this controller
@CrossOrigin(origins = "*") // Allows requests from any origin (important for local VS Code extension
                            // development)
public class ComplexityController {

    private final ComplexityCalculator complexityCalculator;

    /**
     * Constructor for Dependency Injection. Spring will automatically inject
     * an instance of ComplexityCalculator.
     * 
     * @param complexityCalculator The service to perform the actual complexity
     *                             calculation.
     */
    public ComplexityController(ComplexityCalculator complexityCalculator) {
        this.complexityCalculator = complexityCalculator;
    }

    /**
     * Handles POST requests to "/api/complexity/analyze".
     * Expects a JSON request body containing 'code' and 'language'.
     *
     * @param request A ComplexityRequest object populated from the JSON request
     *                body.
     * @return A ResponseEntity containing the analysis results or an error message.
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeCode(@RequestBody ComplexityRequest request) {
        if (request.code() == null || request.code().isEmpty()) {
            return new ResponseEntity<>(Map.of("error", "Code content cannot be empty."), HttpStatus.BAD_REQUEST);
        }
        if (request.language() == null || request.language().isEmpty()) {
            return new ResponseEntity<>(Map.of("error", "Language cannot be empty."), HttpStatus.BAD_REQUEST);
        }

        // Delegate the analysis to the ComplexityCalculator service
        Map<String, Object> analysisResult = complexityCalculator.calculateComplexity(request.code(),
                request.language());

        if (analysisResult.containsKey("error")) {
            // If the analysis service returns an error (e.g., unsupported language),
            // return a 400 Bad Request status.
            return new ResponseEntity<>(analysisResult, HttpStatus.BAD_REQUEST);
        }

        // Return the successful analysis result with a 200 OK status
        return new ResponseEntity<>(analysisResult, HttpStatus.OK);
    }

    /**
     * Provides a list of languages supported by the analyzer.
     * 
     * @return A ResponseEntity containing a map with a list of supported languages.
     */
    @GetMapping("/supported-languages")
    public ResponseEntity<Map<String, Set<String>>> getSupportedLanguages() {
        return new ResponseEntity<>(Map.of("languages", complexityCalculator.getSupportedLanguages()), HttpStatus.OK);
    }
}