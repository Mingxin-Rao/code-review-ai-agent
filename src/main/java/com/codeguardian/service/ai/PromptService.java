package com.codeguardian.service.ai;

import com.codeguardian.entity.Finding;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Prompt service.
 *
 * <p>Builds prompts using Spring AI's PromptTemplate.</p>
 * <p>Builds code-review prompts using Spring AI's {@link PromptTemplate}.</p>
 *
 * @since 1.0.0
 */
@Service
@Slf4j
public class PromptService {

    /**
     * Code-review prompt template.
     */
    private static final String CODE_REVIEW_PROMPT_TEMPLATE = """
        You are a senior code reviewer. Review the following {language} code and identify potential bugs, security vulnerabilities, performance issues, and code-style problems.

        {tool_guidance}

        {context_section}

        **Important requirements:**
        1. Write all field values in English.
        2. The code is already annotated with line numbers (each line is prefixed as "lineNumber: code"). Fill in startLine and endLine strictly according to the line numbers shown in the code.
        3. The location field must use the "fileName:lineNumber" format, e.g. "UserService.java:7". If the code contains a class name, infer the file name from it (a Java class maps to a .java file).
        4. Keep the description concise and to the point — state the problem directly.
        5. Keep the suggestion concise and actionable, in the format "Suggestion: <the concrete fix>".
        6. The diff field must use standard diff format; every line must start with "- " (removed) or "+ " (added), with no other prefixes or explanatory text.
        7. startLine and endLine must match exactly the line numbers shown in front of each line of code — do not use any line numbers that appear inside the code content itself.

        Return the result as a JSON array. Each issue must contain the following fields:
        - severity: severity level (CRITICAL, HIGH, MEDIUM, LOW)
        - title: issue title (in English)
        - location: issue location, in the "fileName:lineNumber" format, e.g. "UserService.java:7"
        - startLine: start line number (integer, required, must match the line number shown in the code)
        - endLine: end line number (integer; equal to startLine for a single line; must match the line number shown in the code)
        - description: issue description (in English, concise, stating the problem directly)
        - suggestion: fix suggestion (in English, in the format "Suggestion: <the concrete fix>")
        - diff: fix diff (standard diff format, each line starting with "- " or "+ ", containing the complete fixed code)
        - category: issue category (SECURITY, PERFORMANCE, BUG, CODE_STYLE, MAINTAINABILITY)

        **Example format:**
        {{
          "severity": "CRITICAL",
          "title": "SQL injection risk",
          "location": "UserService.java:7",
          "startLine": 7,
          "endLine": 7,
          "description": "User input is concatenated directly into the SQL statement, which can lead to SQL injection attacks.",
          "suggestion": "Suggestion: use a PreparedStatement with parameter binding instead of string concatenation.",
          "diff": "- String sql = \\"SELECT * FROM users WHERE name = \\" + username;\\n+ String sql = \\"SELECT * FROM users WHERE name = ?\\";\\n+ PreparedStatement ps = conn.prepareStatement(sql);\\n+ ps.setString(1, username);\\n+ ResultSet rs = ps.executeQuery();",
          "category": "SECURITY"
        }}

        Code (line numbers included):
        ```
        {codeContent}
        ```

        Return the JSON array directly, with no additional explanatory text.
        """;
    
    private final PromptTemplate codeReviewPromptTemplate;
    
    public PromptService() {
        this.codeReviewPromptTemplate = new PromptTemplate(CODE_REVIEW_PROMPT_TEMPLATE);
    }
    
    /**
     * Build the code-review prompt.
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @return the built {@link Prompt}
     */
    public Prompt buildCodeReviewPrompt(String codeContent, String language) {
        return buildCodeReviewPrompt(codeContent, language, null, null);
    }

    /**
     * Build the code-review prompt with retrieved context.
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @param context relevant context retrieved via RAG
     * @return the built {@link Prompt}
     */
    public Prompt buildCodeReviewPrompt(String codeContent, String language, String context) {
        return buildCodeReviewPrompt(codeContent, language, context, null);
    }

    /**
     * Build the code-review prompt with retrieved context and existing findings.
     * Build the code-review prompt with context and pre-existing findings.
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @param context relevant context retrieved via RAG
     * @param existingFindings findings already produced by static analysis
     * @return the built {@link Prompt}
     */
    public Prompt buildCodeReviewPrompt(String codeContent, String language, String context, List<Finding> existingFindings) {
        if (language == null || language.trim().isEmpty()) {
            language = "code";
        }

        // Add line numbers so the model can reference them accurately
        // Annotate the code with line numbers so the model can reference them accurately.
        String codeWithLineNumbers = addLineNumbers(codeContent);

        String contextSection = "";
        if (context != null && !context.trim().isEmpty()) {
            contextSection = "**Reference knowledge base (RAG) — similar issues and fix examples:**\n" + context + "\n";
        }

        String toolGuidance;
        if (existingFindings != null && !existingFindings.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("**Completed static-analysis results:**\n");
            sb.append("The system has already run static-analysis tools (e.g. Semgrep) and found the issues below. Carefully re-review them, include the ones you confirm are real in your final report, and use your expertise to surface deeper issues (such as logic flaws):\n");
            for (Finding f : existingFindings) {
                sb.append(String.format("- [line: %d] [%s] %s\n", f.getStartLine(), f.getSeverity(), f.getDescription()));
            }
            sb.append("\nNote: there is no need to call the static-analysis tools again — focus on logic review and confirming the issues above.\n");
            toolGuidance = sb.toString();
        } else {
            toolGuidance = """
            **Tool capabilities:**
            You have two powerful analysis tools available: `javaSyntaxAnalysis` (syntax checking) and `semgrepAnalysis` (static security scanning).

            **Invocation strategy (entirely up to you):**
            1. **Call on demand**: only invoke a tool when you believe the code has a potential issue that needs tool confirmation.
            2. **Work step by step**: analyze the code logic yourself first; request tool support only when you hit complex syntax structures or uncertain security risks.
            3. **Do not call blindly**: for simple logic or pseudocode, no tool call is needed.
            """;
        }

        Map<String, Object> variables = Map.of(
                "codeContent", codeWithLineNumbers,
                "language", language,
                "context_section", contextSection,
                "tool_guidance", toolGuidance
        );

        Prompt prompt = codeReviewPromptTemplate.create(variables);

        log.debug("Built code-review prompt: language={}, hasContext={}, hasFindings={}, codeLength={}",
                language,
                (context != null && !context.isEmpty()),
                (existingFindings != null && !existingFindings.isEmpty()),
                codeContent.length());

        return prompt;
    }

    /**
     * Prefix each line of the code with its line number.
     */
    private String addLineNumbers(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String[] lines = code.split("\\r?\\n", -1); // keep blank lines

        for (int i = 0; i < lines.length; i++) {
            // format: "1: public class Test {"
            sb.append(i + 1).append(": ").append(lines[i]).append("\n");
        }
        
        return sb.toString();
    }
}

