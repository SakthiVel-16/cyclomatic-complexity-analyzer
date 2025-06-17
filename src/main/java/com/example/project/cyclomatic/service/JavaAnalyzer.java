package com.example.project.cyclomatic.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements the LanguageAnalyzer interface for Java code.
 * Calculates cyclomatic complexity based on control flow statements and logical
 * operators.
 */
@Service
public class JavaAnalyzer implements LanguageAnalyzer {

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public Map<String, Object> analyze(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> methodsList = new ArrayList<>();

        // Pattern to match Java method declarations (simplified)
        Pattern methodPattern = Pattern.compile(
                "(public|protected|private|static|final|abstract|synchronized)\\s+" + // Modifiers
                        "(<[^>]+>\\s+)?([a-zA-Z_$][a-zA-Z0-9_$]*\\s+)*" + // Generic types and return type
                        "([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[^{]*)?\\s*\\{");
        Matcher matcher = methodPattern.matcher(code);
        int totalComplexity = 0;
        int totalMethods = 0;
        int currentIndex = 0;

        while (matcher.find(currentIndex)) {
            String methodName = matcher.group(4); // The method name is in group 4
            int methodSignatureStart = matcher.start();
            int methodBodyStart = matcher.end();
            int methodLine = getLineNumber(code, methodSignatureStart);

            int methodBodyEnd = findMethodEnd(code, methodBodyStart);

            if (methodBodyEnd == -1) {
                System.err.println(
                        "Error: Could not find end of method '" + methodName + "' starting at line " + methodLine);
                // Advance current index to prevent infinite loop on parsing error
                currentIndex = methodSignatureStart + 1;
                continue;
            }

            String methodBody = code.substring(methodBodyStart, methodBodyEnd);
            String cleanedMethodBody = removeComments(methodBody);

            ComplexityResult complexityResult = calculateComplexityWithNesting(cleanedMethodBody);
            int complexity = complexityResult.complexity();
            int nestingDepth = complexityResult.maxNestingDepth();

            String status = getStatus(complexity);

            Map<String, Object> methodData = new LinkedHashMap<>();
            methodData.put("name", methodName);
            methodData.put("line", methodLine);
            methodData.put("complexity", complexity);
            methodData.put("status", status);
            methodData.put("nestingDepth", nestingDepth);

            methodsList.add(methodData);
            totalComplexity += complexity;
            totalMethods++;
            currentIndex = methodBodyEnd; // Continue search from after this method's body
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMethods", totalMethods);
        summary.put("totalComplexity", totalComplexity);

        result.put("summary", summary);
        result.put("methods", methodsList);
        return result;
    }

    /**
     * Calculates the cyclomatic complexity for a given code block (method body).
     * Includes logic for nesting depth penalty.
     *
     * @param code The method body as a string.
     * @return A ComplexityResult object containing the complexity and max nesting
     *         depth.
     */
    private ComplexityResult calculateComplexityWithNesting(String code) {
        int complexity = 1; // Start with base complexity of 1 for the method itself
        String cleanCode = removeStringsAndChars(code);

        // Count control flow statements and calculate nesting
        ComplexityResult controlResult = countControlStructuresWithNesting(cleanCode);
        complexity += controlResult.complexity();
        int maxNestingDepth = controlResult.maxNestingDepth();

        // Count decision points not covered by basic control structures
        complexity += countCaseStatements(cleanCode);
        complexity += countLogicalOperators(cleanCode);
        complexity += countTernaryOperators(cleanCode); // This was the fix
        complexity += countCatchBlocks(cleanCode);
        complexity += countSynchronizedBlocks(cleanCode);

        return new ComplexityResult(complexity, maxNestingDepth);
    }

    /**
     * Removes string and character literals from the code to prevent counting
     * operators inside them.
     *
     * @param code The code string.
     * @return Code string with literals replaced.
     */
    private String removeStringsAndChars(String code) {
        code = code.replaceAll("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", "\"\""); // Double quotes
        code = code.replaceAll("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'", "''"); // Single quotes (chars)
        return code;
    }

    /**
     * Counts control structures and tracks nesting depth based on brace balance.
     *
     * @param code The cleaned code (no comments/strings).
     * @return ComplexityResult with count of structures and max nesting depth.
     */
    private ComplexityResult countControlStructuresWithNesting(String code) {
        int count = 0;
        int currentNestingLevel = 0;
        int maxNestingDepth = 0;

        Pattern ifPattern = Pattern.compile("\\bif\\s*\\(");
        Pattern forPattern = Pattern.compile("\\bfor\\s*\\(");
        Pattern whilePattern = Pattern.compile("\\bwhile\\s*\\(");
        Pattern doPattern = Pattern.compile("\\bdo\\s*\\{");
        Pattern elsePattern = Pattern.compile("\\belse\\b(?!\\s*if\\b)"); // 'else' but not 'else if'
        Pattern tryPattern = Pattern.compile("\\btry\\s*\\{");

        // Split code into lines for easier processing of braces and keywords
        String[] lines = code.split("\\R"); // \R for any Unicode newline sequence

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue; // Skip empty lines

            // Count complexity for keywords, adding nesting penalty if applicable
            // The nesting penalty here applies if a control structure is ALREADY inside a
            // brace-defined block.
            // This is a heuristic and might differ slightly from strict definitions.
            if (ifPattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }
            if (forPattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }
            if (whilePattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }
            if (doPattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }
            if (elsePattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }
            if (tryPattern.matcher(trimmed).find()) {
                count += 1 + (currentNestingLevel > 0 ? 1 : 0);
            }

            // Update nesting level based on braces
            long openBraces = trimmed.chars().filter(ch -> ch == '{').count();
            long closeBraces = trimmed.chars().filter(ch -> ch == '}').count();

            currentNestingLevel += (int) openBraces;
            maxNestingDepth = Math.max(maxNestingDepth, currentNestingLevel); // Track max depth
            currentNestingLevel -= (int) closeBraces;
            currentNestingLevel = Math.max(0, currentNestingLevel); // Ensure it doesn't go below 0
        }

        return new ComplexityResult(count, maxNestingDepth);
    }

    /**
     * Counts `case` and `default` statements within `switch` blocks.
     */
    private int countCaseStatements(String code) {
        int count = 0;
        Pattern casePattern = Pattern.compile("\\bcase\\s+[^:]+:");
        Matcher caseMatcher = casePattern.matcher(code);
        while (caseMatcher.find()) {
            count++;
        }

        Pattern defaultPattern = Pattern.compile("\\bdefault\\s*:");
        Matcher defaultMatcher = defaultPattern.matcher(code);
        while (defaultMatcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts logical AND (&&) and OR (||) operators.
     */
    private int countLogicalOperators(String code) {
        Pattern logicPattern = Pattern.compile("&&|\\|\\|");
        Matcher matcher = logicPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts ternary operators (?).
     * Fix: Simpler regex to count all '?' characters as decision points.
     */
    private int countTernaryOperators(String code) {
        Pattern ternaryPattern = Pattern.compile("\\?"); // Simply count each '?'
        Matcher matcher = ternaryPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts `catch` blocks.
     */
    private int countCatchBlocks(String code) {
        Pattern catchPattern = Pattern.compile("\\bcatch\\s*\\(");
        Matcher matcher = catchPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts `synchronized` blocks/methods (adds complexity).
     */
    private int countSynchronizedBlocks(String code) {
        Pattern synchronizedPattern = Pattern.compile("\\bsynchronized\\b");
        Matcher matcher = synchronizedPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Determines the complexity status based on the calculated score.
     */
    private String getStatus(int complexity) {
        if (complexity <= 5)
            return "simple";
        else if (complexity <= 10)
            return "moderate";
        else
            return "complex";
    }

    /**
     * Finds the closing brace for a given opening brace, respecting nested braces,
     * strings, and comments. This is crucial for isolating method bodies.
     *
     * @param code  The full source code.
     * @param start The index of the opening brace of the block to find the end for.
     * @return The index of the character immediately after the matching closing
     *         brace, or -1 if not found.
     */
    private int findMethodEnd(String code, int start) {
        int openBraces = 1; // We start assuming one opening brace found at 'start'
        boolean inString = false;
        boolean inChar = false;
        boolean inBlockComment = false;
        boolean inLineComment = false;

        for (int i = start; i < code.length(); i++) {
            char ch = code.charAt(i);
            char nextCh = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';
            char prevCh = (i > 0) ? code.charAt(i - 1) : '\0';

            // Check for line comments (//)
            if (!inString && !inChar && !inBlockComment && ch == '/' && nextCh == '/') {
                inLineComment = true;
                i++; // Skip next character as well
                continue;
            }
            if (inLineComment && ch == '\n') {
                inLineComment = false;
                continue;
            }

            // Check for block comments (/* */)
            if (!inString && !inChar && !inLineComment && ch == '/' && nextCh == '*') {
                inBlockComment = true;
                i++; // Skip next character as well
                continue;
            }
            if (inBlockComment && ch == '*' && nextCh == '/') {
                inBlockComment = false;
                i++; // Skip next character as well
                continue;
            }

            // If inside a comment, skip to next character
            if (inBlockComment || inLineComment) {
                continue;
            }

            // Handle string literals (")
            if (ch == '"' && !inChar && !inString && prevCh != '\\') { // prevCh != '\\' to handle escaped quotes
                inString = true;
                continue;
            } else if (ch == '"' && inString && prevCh != '\\') {
                inString = false;
                continue;
            }

            // Handle char literals (')
            if (ch == '\'' && !inString && !inChar && prevCh != '\\') { // prevCh != '\\' to handle escaped quotes
                inChar = true;
                continue;
            } else if (ch == '\'' && inChar && prevCh != '\\') {
                inChar = false;
                continue;
            }

            // If inside a string or char literal, skip to next character
            if (inString || inChar) {
                continue;
            }

            // Count braces if not in comments or strings
            if (ch == '{') {
                openBraces++;
            } else if (ch == '}') {
                openBraces--;
                if (openBraces == 0) {
                    return i + 1; // Return index after the closing brace
                }
            }
        }
        return -1; // No matching brace found
    }

    /**
     * Removes all single-line (//) and multi-line (/* * /) comments from the code.
     *
     * @param code The code string.
     * @return Code string with comments removed.
     */
    private String removeComments(String code) {
        // Remove multi-line comments first (non-greedy)
        code = code.replaceAll("/\\*.*?\\*/", "");
        // Remove single-line comments
        code = code.replaceAll("//.*", "");
        return code;
    }

    /**
     * Gets the line number for a given character index in the code.
     *
     * @param code  The full source code.
     * @param index The character index.
     * @return The line number (1-based).
     */
    private int getLineNumber(String code, int index) {
        int lineNumber = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
}