package com.example.project.cyclomatic.service;

import java.util.Map;

/**
 * Interface for analyzing the cyclomatic complexity of code in a specific
 * programming language.
 */
public interface LanguageAnalyzer {

    /**
     * Returns the name of the language this analyzer supports.
     * (e.g., "java", "javascript", "python")
     * 
     * @return The language name.
     */
    String getLanguage();

    /**
     * Analyzes the given source code to calculate its cyclomatic complexity.
     *
     * @param code The source code string to analyze.
     * @return A Map containing analysis results, typically with a "summary"
     *         and a "methods" list, where each method entry is also a Map.
     */
    Map<String, Object> analyze(String code);
}