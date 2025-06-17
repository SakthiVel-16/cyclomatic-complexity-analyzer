package com.example.project.cyclomatic.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements the LanguageAnalyzer interface for Python code.
 * <p>
 * Due to Python's indentation-based syntax, accurately determining
 * nesting depth and method body boundaries purely with regular expressions is
 * challenging. This analyzer provides a heuristic-based approach.
 * <p>
 * Nesting depth for Python with this regex-based approach will be a rough
 * estimation.
 * For robust Python analysis, a dedicated AST (Abstract Syntax Tree) parser is
 * recommended.
 */
@Service
public class PythonAnalyzer implements LanguageAnalyzer {

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public Map<String, Object> analyze(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> methodsList = new ArrayList<>();

        // Pattern to match Python function definitions (def function_name(...):)
        // ^\\s* ensures it matches only at the beginning of a line (due to
        // Pattern.MULTILINE)
        Pattern functionPattern = Pattern.compile("^\\s*def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\([^)]*\\):",
                Pattern.MULTILINE);

        Matcher matcher = functionPattern.matcher(code);
        int totalComplexity = 0;
        int totalMethods = 0;
        int currentIndex = 0;

        while (matcher.find(currentIndex)) {
            String functionName = matcher.group(1);
            int functionDefStart = matcher.start();
            int functionLine = getLineNumber(code, functionDefStart);

            // Get the actual function block, including the 'def' line and its indented body
            int functionBlockEnd = findPythonFunctionBlockEnd(code, functionDefStart);

            // Handle cases where the block end might be invalid (e.g., EOF or malformed
            // code)
            if (functionBlockEnd <= functionDefStart) {
                System.err.println("Warning: Could not determine valid end for function '" + functionName
                        + "' starting at line " + functionLine);
                currentIndex = functionDefStart + 1; // Advance to avoid infinite loop
                continue;
            }

            String fullFunctionBlock = code.substring(functionDefStart, functionBlockEnd);

            // Analyze the full function block to get its complexity and nesting
            ComplexityResult complexityResult = calculateComplexityWithNesting(fullFunctionBlock);
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

            currentIndex = functionBlockEnd; // Move current index past the current function's analyzed block
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalMethods", totalMethods);
        summary.put("totalComplexity", totalComplexity);

        result.put("summary", summary);
        result.put("methods", methodsList);
        return result;
    }

    /**
     * Finds the end of a Python function block by analyzing indentation levels.
     * It determines the function's body by finding the first non-empty, non-comment
     * line
     * after the 'def' statement, and then determines the end when the indentation
     * drops below that initial body indentation, or when another 'def' is found
     * at or below the original 'def's indentation.
     *
     * @param code             The full source code string.
     * @param functionDefStart The starting index of the 'def' statement.
     * @return The index immediately after the last character of the function's
     *         block.
     */
    private int findPythonFunctionBlockEnd(String code, int functionDefStart) {
        // Get the indentation level of the 'def' statement
        int defLineStart = code.lastIndexOf('\n', functionDefStart) + 1;
        if (defLineStart == -1)
            defLineStart = 0; // If 'def' is on the very first line

        int initialIndent = 0;
        while (defLineStart + initialIndent < code.length()
                && Character.isWhitespace(code.charAt(defLineStart + initialIndent))) {
            initialIndent++;
        }

        String remainingCode = code.substring(defLineStart);
        String[] lines = remainingCode.split("\\R");

        int bodyMinIndent = -1; // Minimum indentation expected for the function's body

        // First pass: Find the indentation level of the first actual code line in the
        // function's body
        for (int i = 1; i < lines.length; i++) { // Start from the line after 'def'
            String currentLine = lines[i];
            String trimmedCurrentLine = currentLine.trim();

            if (trimmedCurrentLine.isEmpty() || trimmedCurrentLine.startsWith("#") ||
                    trimmedCurrentLine.startsWith("'''") || trimmedCurrentLine.startsWith("\"\"\"")) {
                continue; // Ignore blank lines, comments, and docstrings for body indent calculation
            }

            int indentOfFirstBodyLine = 0;
            while (indentOfFirstBodyLine < currentLine.length()
                    && Character.isWhitespace(currentLine.charAt(indentOfFirstBodyLine))) {
                indentOfFirstBodyLine++;
            }
            bodyMinIndent = indentOfFirstBodyLine;
            break; // Found the reference indent for the body, exit this loop
        }

        // If bodyMinIndent is still -1, it means the function body is empty or only
        // comments/docstrings
        // In this case, the function ends immediately after the 'def' line.
        if (bodyMinIndent == -1) {
            return functionDefStart + lines[0].length() + 1; // End right after the def line's newline
        }

        // Second pass: Iterate through lines to find the end of the block based on
        // indentation
        int cumulativeChars = 0; // Tracks total characters processed from defLineStart
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            cumulativeChars += line.length() + 1; // +1 for the newline character

            if (i == 0)
                continue; // Skip the 'def' line itself (already processed its indent)

            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") ||
                    trimmedLine.startsWith("'''") || trimmedLine.startsWith("\"\"\"")) {
                continue; // Ignore blank lines, comments, docstrings for block end determination
            }

            int currentLineIndent = 0;
            while (currentLineIndent < line.length() && Character.isWhitespace(line.charAt(currentLineIndent))) {
                currentLineIndent++;
            }

            // If the current line's indentation is strictly less than the function body's
            // minimum indentation,
            // it means the function block has ended.
            // Also, if it's another 'def' at the same or lower indent than the *initial*
            // def, it's the end.
            if (currentLineIndent < bodyMinIndent ||
                    (currentLineIndent <= initialIndent && trimmedLine.startsWith("def "))) {
                return defLineStart + cumulativeChars - (line.length() + 1); // Return start of this line
            }
        }
        return code.length(); // If no such line found, function extends to end of code
    }

    /**
     * Calculates the complexity for a full function block (including the 'def'
     * line).
     *
     * @param fullFunctionBlock The Python function block including the 'def' line.
     * @return A ComplexityResult object containing the complexity and max nesting
     *         depth.
     */
    private ComplexityResult calculateComplexityWithNesting(String fullFunctionBlock) {
        int complexity = 1; // Base complexity of 1 for the function itself (the 'def' statement)

        // Clean the block for analysis - ensure these are applied to the full block
        // before splitting
        String cleanedBlock = removeCommentsAndDocstrings(fullFunctionBlock);
        cleanedBlock = removeStrings(cleanedBlock);

        int maxNestingDepth = 0;
        int defLineIndent = -1; // To store the indentation of the 'def' line

        // Python's decision points patterns (colons are mandatory in Python syntax)
        // FIX for if/elif: now correctly matches 'if condition:' and 'elif condition:'
        Pattern ifElifElsePattern = Pattern.compile("\\b(if|elif)\\b[^:]*:\\s*|\\b(else)\\s*:");

        Pattern forWhilePattern = Pattern.compile("\\b(for|while)\\b[^:]*:\\s*"); // Added \\s* for consistency

        // Adjusted: Relaxed the \\b after keyword and added \\s* after colon.
        Pattern tryExceptFinallyPattern = Pattern.compile("\\b(try|except|finally|with)\\b[^:]*\\s*:");

        Pattern logicalOperatorPattern = Pattern.compile("\\b(and|or)\\b"); // Python uses 'and'/'or' keywords
        // Python's ternary operator: value_if_true if condition else value_if_false
        Pattern ternaryOperatorPattern = Pattern.compile("\\b\\S+\\s+if\\s+.*?\\s+else\\s+[^\\s]+\\b");

        String[] lines = cleanedBlock.split("\\R");

        // Iterate through lines to calculate complexity and nesting depth
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty())
                continue;

            int leadingSpaces = 0;
            while (leadingSpaces < line.length() && Character.isWhitespace(line.charAt(leadingSpaces))) {
                leadingSpaces++;
            }

            if (defLineIndent == -1) { // Capture initial indent from the 'def' line
                defLineIndent = leadingSpaces;
            }

            // Calculate relative indent level for nesting, relative to the function's 'def'
            // statement
            int relativeIndent = leadingSpaces - defLineIndent;
            if (relativeIndent < 0)
                relativeIndent = 0; // Should not happen for lines within the block

            // Update max nesting depth: Each "level" of indentation adds to nesting.
            // Assuming 4 spaces per indent level for this calculation (common Python
            // style).
            maxNestingDepth = Math.max(maxNestingDepth, relativeIndent / 4);

            // Skip the 'def' line itself from *additional* complexity calculation
            // as its base '1' is already added.
            if (trimmedLine.startsWith("def ")) {
                continue;
            }

            // Count decision points
            if (ifElifElsePattern.matcher(trimmedLine).find()) {
                complexity++;
            }
            if (forWhilePattern.matcher(trimmedLine).find()) {
                complexity++;
            }
            if (tryExceptFinallyPattern.matcher(trimmedLine).find()) {
                complexity++;
            }

            // Iterate through ALL matches for logical operators on the line (e.g., "A and B
            // or C")
            Matcher logicalMatcher = logicalOperatorPattern.matcher(trimmedLine);
            while (logicalMatcher.find()) {
                complexity++;
            }

            if (ternaryOperatorPattern.matcher(trimmedLine).find()) {
                complexity++;
            }
        }
        return new ComplexityResult(complexity, maxNestingDepth);
    }

    /**
     * Removes single-line comments (#) and multi-line docstrings (triple quotes).
     *
     * @param code The code string.
     * @return Code string with comments and docstrings removed.
     */
    private String removeCommentsAndDocstrings(String code) {
        // Remove single-line comments first, as they are simpler
        code = code.replaceAll("#.*", "");

        // Remove multi-line docstrings (triple quotes) - non-greedy
        // Matches either """ or ''' and then any characters (including newlines)
        // non-greedily,
        // until it finds the same triple quote sequence that opened it. DOTALL is
        // crucial here.
        code = Pattern.compile("(\"\"\"|''')[\\s\\S]*?\\1", Pattern.DOTALL).matcher(code).replaceAll("");
        return code;
    }

    /**
     * Removes string literals (single and double quoted), handling escaped quotes.
     *
     * @param code The code string.
     * @return Code string with string literals replaced with empty strings.
     */
    private String removeStrings(String code) {
        // Remove single-quoted strings, handling escaped single quotes
        code = code.replaceAll("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'", "''");
        // Remove double-quoted strings, handling escaped double quotes
        code = code.replaceAll("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", "\"\"");
        return code;
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
     * Gets the line number for a given character index in the code.
     *
     * @param code  The full source code.
     * @param index The character index.
     * @return The line number (1-based).
     */
    private int getLineNumber(String code, int intIndex) {
        int lineNumber = 1;
        for (int i = 0; i < intIndex && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
}