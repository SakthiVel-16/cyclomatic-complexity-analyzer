package com.example.project.cyclomatic.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements the LanguageAnalyzer interface for JavaScript code.
 * Adapts the logic from JavaAnalyzer for JavaScript's syntax, especially
 * regarding function detection, control structures, and comment/string
 * handling.
 */
@Service
public class JavaScriptAnalyzer implements LanguageAnalyzer {

    @Override
    public String getLanguage() {
        return "javascript";
    }

    @Override
    public Map<String, Object> analyze(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> methodsList = new ArrayList<>();

        // Pattern to match various JavaScript function declarations and methods
        // This pattern attempts to capture:
        // 1. Named function declarations: `function funcName(...) {` (group 1)
        // 2. Function expressions: `const/let/var funcName = function(...) {` (group 2:
        // funcName)
        // 3. Arrow functions: `const/let/var funcName = (...) => {` (group 3: funcName)
        // 4. Class methods/Object methods: `methodName(...) {` (group 4: methodName)
        Pattern functionPattern = Pattern.compile(
                "(?:\\bfunction\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\([^)]*\\)\\s*\\{|" + // Group 1: Named function
                        "(?:const|let|var)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)?\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{|" + // Group
                                                                                                                     // 2:
                                                                                                                     // Function
                                                                                                                     // expression
                        "(?:const|let|var)?\\s*([a-zA-Z_$][a-zA-Z0-9_$]*)?\\s*=\\s*\\([^)]*\\)\\s*=>\\s*\\{|" + // Group
                                                                                                                // 3:
                                                                                                                // Arrow
                                                                                                                // function
                        "([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\([^)]*\\)\\s*\\{" + // Group 4: Class/Object method (must have
                                                                             // '(' immediately after name)
                        ")");

        Matcher matcher = functionPattern.matcher(code);
        int totalComplexity = 0;
        int totalMethods = 0;
        int currentIndex = 0;

        while (matcher.find(currentIndex)) {
            String functionName = null;
            // Iterate through all capturing groups to find the non-null name
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    functionName = matcher.group(i);
                    break;
                }
            }

            if (functionName == null || functionName.trim().isEmpty()) {
                functionName = "anonymous_or_unnamed_function_" + (totalMethods + 1); // Assign a generic name for
                                                                                      // anonymous/unnamed functions
            }

            int functionSignatureStart = matcher.start();
            int functionBodyStart = matcher.end(); // Index right after the opening brace of the function signature
            int functionLine = getLineNumber(code, functionSignatureStart);

            int functionBodyEnd = findBlockEnd(code, functionBodyStart); // Reusing brace balancing logic

            if (functionBodyEnd == -1) {
                System.err.println("Error: Could not find end of function '" + functionName + "' starting at line "
                        + functionLine);
                currentIndex = functionSignatureStart + 1; // Advance to avoid re-processing same problematic area
                continue;
            }

            String functionBody = code.substring(functionBodyStart, functionBodyEnd);
            String cleanedFunctionBody = removeComments(functionBody); // Remove comments before string/char removal
            cleanedFunctionBody = removeStringsAndChars(cleanedFunctionBody); // Remove strings/template literals

            ComplexityResult complexityResult = calculateComplexityWithNesting(cleanedFunctionBody);
            int complexity = complexityResult.complexity();
            int nestingDepth = complexityResult.maxNestingDepth();

            String status = getStatus(complexity);

            Map<String, Object> methodData = new LinkedHashMap<>();
            methodData.put("name", functionName);
            methodData.put("line", functionLine);
            methodData.put("complexity", complexity);
            methodData.put("status", status);
            methodData.put("nestingDepth", nestingDepth);

            methodsList.add(methodData);
            totalComplexity += complexity;
            totalMethods++;
            currentIndex = functionBodyEnd;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMethods", totalMethods);
        summary.put("totalComplexity", totalComplexity);

        result.put("summary", summary);
        result.put("methods", methodsList);
        return result;
    }

    private ComplexityResult calculateComplexityWithNesting(String code) {
        int complexity = 1; // Base complexity for a function

        // No need to call removeStringsAndChars here again if already done in analyze
        // method
        // String cleanCode = removeStringsAndChars(code); // Moved to analyze method

        ComplexityResult controlResult = countControlStructuresWithNesting(code); // Use already cleaned code
        complexity += controlResult.complexity();
        int maxNestingDepth = controlResult.maxNestingDepth();

        complexity += countCaseStatements(code);
        complexity += countLogicalOperators(code);
        complexity += countTernaryOperators(code);
        complexity += countCatchBlocks(code);

        return new ComplexityResult(complexity, maxNestingDepth);
    }

    private String removeStringsAndChars(String code) {
        // Remove double-quoted strings
        code = code.replaceAll("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", "\"\"");
        // Remove single-quoted strings
        code = code.replaceAll("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'", "''");
        // Remove backtick-quoted template literals (multi-line)
        code = code.replaceAll("`.*?`", "``");
        return code;
    }

    private ComplexityResult countControlStructuresWithNesting(String code) {
        int count = 0;
        int currentNestingLevel = 0;
        int maxNestingDepth = 0;

        Pattern ifPattern = Pattern.compile("\\bif\\s*\\(");
        Pattern forPattern = Pattern.compile("\\bfor\\s*\\(");
        Pattern whilePattern = Pattern.compile("\\bwhile\\s*\\(");
        Pattern doPattern = Pattern.compile("\\bdo\\s*\\{");
        Pattern elsePattern = Pattern.compile("\\belse\\b(?!\\s*if\\b)");
        Pattern tryPattern = Pattern.compile("\\btry\\s*\\{");

        String[] lines = code.split("\\R");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;

            // REMOVED THE NESTING PENALTY (currentNestingLevel > 0 ? 1 : 0)
            if (ifPattern.matcher(trimmed).find()) {
                count++;
            }
            if (forPattern.matcher(trimmed).find()) {
                count++;
            }
            if (whilePattern.matcher(trimmed).find()) {
                count++;
            }
            if (doPattern.matcher(trimmed).find()) {
                count++;
            }
            if (elsePattern.matcher(trimmed).find()) {
                count++;
            }
            if (tryPattern.matcher(trimmed).find()) {
                count++;
            }

            // Nesting level calculation based on braces remains the same
            long openBraces = trimmed.chars().filter(ch -> ch == '{').count();
            long closeBraces = trimmed.chars().filter(ch -> ch == '}').count();

            currentNestingLevel += (int) openBraces;
            maxNestingDepth = Math.max(maxNestingDepth, currentNestingLevel);
            currentNestingLevel -= (int) closeBraces;
            currentNestingLevel = Math.max(0, currentNestingLevel);
        }

        return new ComplexityResult(count, maxNestingDepth);
    }

    // ... (rest of the class)
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

    private int countLogicalOperators(String code) {
        Pattern logicPattern = Pattern.compile("&&|\\|\\|");
        Matcher matcher = logicPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countTernaryOperators(String code) {
        Pattern ternaryPattern = Pattern.compile("\\?"); // Simply count each '?'
        Matcher matcher = ternaryPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countCatchBlocks(String code) {
        Pattern catchPattern = Pattern.compile("\\bcatch\\s*\\(");
        Matcher matcher = catchPattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String getStatus(int complexity) {
        if (complexity <= 5)
            return "simple";
        else if (complexity <= 10)
            return "moderate";
        else
            return "complex";
    }

    private int getLineNumber(String code, int index) {
        int lineNumber = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    // Reusing the general brace-balancing logic from JavaAnalyzer
    private int findBlockEnd(String code, int start) {
        int openBraces = 1;
        boolean inStringDouble = false; // For "
        boolean inStringSingle = false; // For '
        boolean inStringBacktick = false; // For `
        boolean inBlockComment = false;
        boolean inLineComment = false;

        for (int i = start; i < code.length(); i++) {
            char ch = code.charAt(i);
            char nextCh = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';
            char prevCh = (i > 0) ? code.charAt(i - 1) : '\0';

            // Check for line comments (//)
            if (!inStringDouble && !inStringSingle && !inStringBacktick && !inBlockComment && ch == '/'
                    && nextCh == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (inLineComment && ch == '\n') {
                inLineComment = false;
                continue;
            }

            // Check for block comments (/* */)
            if (!inStringDouble && !inStringSingle && !inStringBacktick && !inLineComment && ch == '/'
                    && nextCh == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (inBlockComment && ch == '*' && nextCh == '/') {
                inBlockComment = false;
                i++;
                continue;
            }

            if (inBlockComment || inLineComment) {
                continue;
            }

            // Handle string literals (", ', and `)
            if (ch == '"' && prevCh != '\\' && !inStringSingle && !inStringBacktick) {
                inStringDouble = !inStringDouble;
                continue;
            }
            if (ch == '\'' && prevCh != '\\' && !inStringDouble && !inStringBacktick) {
                inStringSingle = !inStringSingle;
                continue;
            }
            if (ch == '`' && prevCh != '\\' && !inStringDouble && !inStringSingle) {
                inStringBacktick = !inStringBacktick;
                continue;
            }

            if (inStringDouble || inStringSingle || inStringBacktick) {
                continue;
            }

            if (ch == '{') {
                openBraces++;
            } else if (ch == '}') {
                openBraces--;
                if (openBraces == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private String removeComments(String code) {
        code = code.replaceAll("/\\*.*?\\*/", ""); // Multi-line comments
        code = code.replaceAll("//.*", ""); // Single-line comments
        return code;
    }
}