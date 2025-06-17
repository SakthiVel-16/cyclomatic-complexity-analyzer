package com.example.project.cyclomatic.service;

import org.springframework.stereotype.Service;

import java.util.List; // Add this import
import java.util.Map;
import java.util.Set; // Add this import if not already there
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the complexity analysis by delegating to specific language
 * analyzers.
 */
@Service
public class ComplexityCalculator {

    private final Map<String, LanguageAnalyzer> analyzers;

    // Modify constructor to accept a List of LanguageAnalyzer
    public ComplexityCalculator(List<LanguageAnalyzer> analyzerList) {
        // Collect all LanguageAnalyzer implementations into a map for easy lookup by
        // language name
        this.analyzers = analyzerList.stream()
                .collect(Collectors.toMap(LanguageAnalyzer::getLanguage, Function.identity()));
    }

    /**
     * Calculates the cyclomatic complexity for the given code in the specified
     * language.
     *
     * @param code     The source code to analyze.
     * @param language The programming language of the code (e.g., "java", "python",
     *                 "javascript").
     * @return A map containing the analysis results, or an error message if the
     *         language is not supported.
     */
    public Map<String, Object> calculateComplexity(String code, String language) {
        LanguageAnalyzer analyzer = analyzers.get(language.toLowerCase());
        if (analyzer == null) {
            return Map.of("error", "Unsupported language for complexity analysis: " + language,
                    "Supported Languages", analyzers.keySet());
        }
        return analyzer.analyze(code);
    }

    /**
     * Returns a set of all languages supported by this calculator.
     *
     * @return A Set of strings, where each string is a supported language name.
     */
    public Set<String> getSupportedLanguages() { // NEW METHOD
        return analyzers.keySet();
    }
}