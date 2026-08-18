package com.codeguardian.service;

import com.codeguardian.config.AIConfigProperties;
import com.codeguardian.entity.Finding;
import com.codeguardian.enums.ModelProviderEnum;
import com.codeguardian.service.ai.factory.ChatClientFactory;
import com.codeguardian.service.rag.KnowledgeBaseService;
import com.codeguardian.service.ai.PromptService;
import com.codeguardian.service.ai.context.ReviewContextHolder;
import com.codeguardian.service.ai.output.CodeReviewOutputParser;
import com.codeguardian.service.ai.tool.ToolRegistry;
import org.springframework.ai.model.function.FunctionCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI model service.
 *
 * <p>Runs code review using Spring AI's ChatClient and PromptTemplate.</p>
 *
 * @since 1.0.0
 */
@Service
@Lazy
@RequiredArgsConstructor
@Slf4j
public class AIModelService {

    private final ChatClientFactory chatClientFactory;
    private final PromptService promptService;
    private final CodeReviewOutputParser outputParser;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AIConfigProperties aiConfigProperties;
    private final ToolRegistry toolRegistry;

    /**
     * Review the given code.
     * Review code.
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @param modelProvider the model provider (optional; falls back to the configured provider when null)
     * @param enableRag whether to enable RAG knowledge-base enhancement
     * @return the list of findings
     */
    public List<Finding> reviewCode(String codeContent, String language, String modelProvider, boolean enableRag) {
        return reviewCode(codeContent, language, modelProvider, enableRag, null);
    }

    /**
     * Review the given code, seeded with findings already produced by static analysis.
     * Review code (with pre-existing findings).
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @param modelProvider the model provider
     * @param enableRag whether to enable RAG
     * @param existingFindings findings already produced by static analysis
     * @return the list of findings
     */
    public List<Finding> reviewCode(String codeContent, String language, String modelProvider, boolean enableRag, List<Finding> existingFindings) {
        if (Boolean.FALSE.equals(aiConfigProperties.getEnabled())) {
            log.warn("LLM calls are disabled (ai.enabled=false); skipping the LLM review");
            return new ArrayList<>();
        }
        if (!chatClientFactory.hasAvailableProviders()) {
            log.warn("No available LLM is configured; skipping the LLM review");
            return new ArrayList<>();
        }
        String finalProvider = modelProvider != null ? modelProvider : "default";
        log.info("========== Starting AI code review ==========");
        log.info("Language: {}, code length: {} chars, model provider: {}, RAG enhancement: {}, existing findings: {}",
                language, codeContent.length(), finalProvider, enableRag, existingFindings != null ? existingFindings.size() : 0);

        try {
            // clear stale data from the context
            ReviewContextHolder.clear();

            // RAG retrieval (knowledge-base enhancement)
            String context = null;
            if (enableRag) {
                context = retrieveContext(codeContent, language);
            }

            // build the prompt (using Spring AI's PromptTemplate)
            long promptStartTime = System.currentTimeMillis();
            Prompt prompt = promptService.buildCodeReviewPrompt(codeContent, language, context, existingFindings);
            long promptBuildTime = System.currentTimeMillis() - promptStartTime;

            // get the prompt text
            String promptText = prompt.getContents();
            log.info("Prompt built in {}ms, prompt length: {} chars",
                    promptBuildTime, promptText.length());

            // log the full prompt text
            log.info("========== Prompt sent to the LLM ==========");
            log.info("{}", promptText);
            log.info("========== end of prompt ==========");

            // obtain the ChatClient (Spring AI)
            ModelProviderEnum resolvedProvider = ModelProviderEnum.from(modelProvider).orElse(null);
            ChatClient chatClient = chatClientFactory.createChatClient(resolvedProvider);
            log.info("Using model provider: {}", resolvedProvider != null ? resolvedProvider.getCode() : "default");

            // gather all registered tool callbacks
            List<FunctionCallback> toolCallbacks = new ArrayList<>(toolRegistry.getFunctionCallbacks());

            // When static-analysis findings are already supplied, drop the semgrepAnalysis tool to avoid a duplicate scan
            // If static-analysis results already exist, drop the semgrepAnalysis tool to avoid running it twice.
            if (existingFindings != null && !existingFindings.isEmpty()) {
                toolCallbacks.removeIf(callback -> callback.getName().equalsIgnoreCase("semgrepAnalysis"));
                log.info("Static-analysis results were provided; automatically disabling the Semgrep tool to avoid duplicate runs");
            }

            if (!toolCallbacks.isEmpty()) {
                log.info("Enabled tools: {}", toolCallbacks.stream().map(FunctionCallback::getName).toList());
            } else {
                log.warn("No registered tools found; performing a text-only review");
            }

            // call the AI API (Spring AI ChatClient)
            long apiStartTime = System.currentTimeMillis();
            log.info("[Step 1] Sending the request to the LLM (waiting for a response or a function-calling request)...");

            String response = chatClient.prompt(prompt)
                    .functions(toolCallbacks.toArray(new FunctionCallback[0]))
                    .call()
                    .content();

            log.info("[Step Final] LLM final response complete");
            long apiCallTime = System.currentTimeMillis() - apiStartTime;

            log.info("AI API call succeeded: response time={}ms, response length={} chars",
                    apiCallTime, response != null ? response.length() : 0);

            // log the raw model output (for debugging)
            if (response != null) {
                log.info("========== Raw model output ==========");
                log.info("{}", response);
                log.info("========== end of model output ==========");
            } else {
                log.warn("The model returned empty content");
            }

            // parse the response (Spring AI OutputParser)
            long parseStartTime = System.currentTimeMillis();
            List<Finding> findings = outputParser.parse(response);
            long parseTime = System.currentTimeMillis() - parseStartTime;

            // merge findings surfaced by tools (e.g. Semgrep)
            List<Finding> toolFindings = ReviewContextHolder.getFindings();
            if (!toolFindings.isEmpty()) {
                log.info("Merging {} issue(s) from the tool context", toolFindings.size());
                // Simple de-duplication: identical location and title means duplicate
                // Simple dedup: treat a finding as a duplicate if the line and title match.
                for (Finding tf : toolFindings) {
                    boolean exists = findings.stream().anyMatch(f ->
                        (f.getStartLine() != null && f.getStartLine().equals(tf.getStartLine())) &&
                        (f.getTitle() != null && f.getTitle().contains(tf.getTitle()))
                    );

                    if (!exists) {
                        findings.add(tf);
                    }
                }
            }

            log.info("========== AI review complete ==========");
            log.info("Provider: {}, issues found: {}, total time: {}ms, parse time: {}ms",
                    finalProvider,
                    findings.size(),
                    System.currentTimeMillis() - promptStartTime,
                    parseTime);

            if (findings.isEmpty()) {
                log.warn("No issues found; you may want to check the prompt or the model response");
            } else {
                // tally findings by severity
                long criticalCount = findings.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).count();
                long highCount = findings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
                long mediumCount = findings.stream().filter(f -> "MEDIUM".equals(f.getSeverity())).count();
                long lowCount = findings.stream().filter(f -> "LOW".equals(f.getSeverity())).count();
                log.info("Issue tally: CRITICAL={}, HIGH={}, MEDIUM={}, LOW={}",
                        criticalCount, highCount, mediumCount, lowCount);
            }

            return findings;

        } catch (Exception e) {
            log.error("========== AI review error ==========");
            log.error("Provider: {}, error message: {}", finalProvider, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Review the given code using the configured default model provider.
     * Review code (using the configured default model provider).
     *
     * @param codeContent the code to review
     * @param language the programming language
     * @return the list of findings
     */
    public List<Finding> reviewCode(String codeContent, String language) {
        return reviewCode(codeContent, language, null, true);
    }

    /**
     * List the available model providers.
     * Get the list of available model providers.
     *
     * @return the list of provider names
     */
    public List<ModelProviderEnum> getAvailableProviders() {
        return chatClientFactory.getAvailableProviders();
    }

    /**
     * Retrieve supporting context from the knowledge base.
     * Retrieve knowledge-base context.
     */
    private String retrieveContext(String code, String language) {
        try {
            if (knowledgeBaseService == null) return null;

            // Build the query from the language and the first 500 chars of the snippet (a short query reduces noise)
            // Build the query from the language and the first 500 chars of the snippet (short query reduces noise).
            String query = "Language: " + language + "\nCode Snippet: " +
                    code.substring(0, Math.min(code.length(), 500));

            // Use searchSnippets to fetch chunks rather than whole documents.
            List<String> snippets = knowledgeBaseService.searchSnippets(query, 3);
            if (snippets.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            sb.append("Relevant coding standards and best practices:\n");
            for (int i = 0; i < snippets.size(); i++) {
                sb.append(i + 1).append(". ").append(snippets.get(i)).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
            return null;
        }
    }
}
