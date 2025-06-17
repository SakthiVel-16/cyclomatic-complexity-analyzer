package com.example.project.cyclomatic.service;

/**
 * A record to hold the result of complexity calculation for a code block,
 * including its cyclomatic complexity and maximum nesting depth.
 *
 * @param complexity      The calculated cyclomatic complexity.
 * @param maxNestingDepth The maximum nesting depth observed.
 */
public record ComplexityResult(int complexity, int maxNestingDepth) {
}