package com.example.project.cyclomatic.controller;

/**
 * Data Transfer Object (DTO) to encapsulate the request body for complexity
 * analysis.
 * This class will map the JSON payload received by the REST endpoint.
 *
 * @param code     The source code string to be analyzed.
 * @param language The programming language of the code (e.g., "java", "python",
 *                 "javascript").
 */
public record ComplexityRequest(String code, String language) {
    // Records automatically generate constructor, getters, equals(), hashCode(),
    // and toString().
}