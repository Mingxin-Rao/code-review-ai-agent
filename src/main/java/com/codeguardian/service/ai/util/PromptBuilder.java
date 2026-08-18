package com.codeguardian.service.ai.util;

import lombok.experimental.UtilityClass;

/**
 * Prompt building utilities.
 * Prompt-building utility class.
 *
 * <p>Provides a single entry point for building prompts.</p>
 * <p>Provides a single, shared way to build code-review prompts.</p>
 *
 * @since 1.0.0
 */
@UtilityClass
public class PromptBuilder {

    /**
     * Build the code-review prompt.
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @return the built prompt string
     */
    public String buildCodeReviewPrompt(String codeContent, String language) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a senior code reviewer. Review the following ");
        if (language != null && !language.isEmpty()) {
            prompt.append(language).append(" ");
        }
        prompt.append("code and identify potential bugs, security vulnerabilities, performance issues, and code-style problems.\n\n");
        prompt.append("Return the result as a JSON array, and:\n");
        prompt.append("- Write all field values in English.\n");
        prompt.append("- Use the 'fileName:lineNumber' format for the location field, e.g. 'UserService.java:7'.\n");
        prompt.append("- If the file name cannot be determined, still provide the line number and infer the file name as best you can.\n\n");
        prompt.append("Each issue must contain the following fields:\n");
        prompt.append("- severity: severity level (CRITICAL, HIGH, MEDIUM, LOW)\n");
        prompt.append("- title: issue title\n");
        prompt.append("- location: issue location description\n");
        prompt.append("- startLine: start line number (optional)\n");
        prompt.append("- endLine: end line number (optional)\n");
        prompt.append("- description: issue description\n");
        prompt.append("- suggestion: fix suggestion\n");
        prompt.append("- diff: fix diff (use a unified diff, or the common +/- line-prefix form)\n");
        prompt.append("- category: issue category (SECURITY, PERFORMANCE, BUG, CODE_STYLE, MAINTAINABILITY)\n\n");
        prompt.append("Code:\n");
        prompt.append("```\n");
        prompt.append(codeContent);
        prompt.append("\n```\n\n");
        prompt.append("Return the JSON array directly, with no additional explanatory text.");

        return prompt.toString();
    }
}
