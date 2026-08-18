package com.codeguardian.service.ai.tools;

import com.codeguardian.entity.Finding;
import com.codeguardian.service.ai.context.ReviewContextHolder;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Semgrep analysis tool.
 *
 * <p>Runs a local Semgrep scan. Throws if the Semgrep executable cannot be found;
 * there is no built-in regex fallback.</p>
 */
@Component("semgrepAnalysis")
@Description("Scan the code for security vulnerabilities using Semgrep")
@Slf4j
@RequiredArgsConstructor
public class SemgrepAnalyzerTool implements Function<SemgrepAnalyzerTool.Request, SemgrepAnalyzerTool.Response> {

    private final ObjectMapper objectMapper;

    @Override
    public Response apply(Request request) {
        log.info("[Function Calling] AI model requested the Semgrep analysis tool...");
        long startTime = System.currentTimeMillis();

        try {
            Response response = runSemgrepAnalysis(request);

            // Record findings in the context so they survive even if the model ignores them
            // Add findings to the context so they are captured downstream even if the model ignores them.
            if (response.findings != null && !response.findings.isEmpty()) {
                log.info("Added {} Semgrep findings to ReviewContextHolder", response.findings.size());
                ReviewContextHolder.addFindings(response.findings);
            }

            log.info("Semgrep analysis complete in {} ms, found {} issues", System.currentTimeMillis() - startTime, response.vulnerabilities != null ? response.vulnerabilities.size() : 0);
            return response;
        } catch (Exception e) {
            log.error("Semgrep run failed (took {} ms): {}", System.currentTimeMillis() - startTime, e.getMessage());
            return new Response(false, List.of("Semgrep analysis failed: " + e.getMessage()), "An error occurred during analysis; please check the environment configuration.");
        }
    }

    public Response runSemgrepAnalysis(Request request) throws Exception {
        String semgrepPath = resolveSemgrepPath();
        if (semgrepPath == null) {
            throw new RuntimeException("Semgrep executable not found in PATH or standard locations. Please install semgrep.");
        }

        // Auto-wrap the snippet in a class when it has no class declaration, to help Semgrep parse it
        // Auto-wrap the snippet: if it has no class definition, wrap it in a class to help Semgrep parse it.
        String codeToScan = request.code;
        if (!codeToScan.contains("class ") && !codeToScan.contains("interface ") && !codeToScan.contains("enum ")) {
            codeToScan = "public class SemgrepWrapper {\n" + codeToScan + "\n}";
            log.info("Snippet appears to lack a class definition; auto-wrapped it in a SemgrepWrapper class to improve parse success");
        }

        // The Semgrep CLI needs a file path, so write the code to a temp file
        // The Semgrep CLI usually needs a file path, so write the code to a temp file.
        Path tempFile = Files.createTempFile("semgrep_scan_", ".java");
        Files.writeString(tempFile, codeToScan);
        log.debug("Created temp file for the Semgrep scan: {}", tempFile);

        try {
            log.info("Starting Semgrep process scan: {}", semgrepPath);
            // use the p/default ruleset
            ProcessBuilder processBuilder = new ProcessBuilder(
                    semgrepPath,
                    "--config", "p/default", 
                    "--json",
                    "--quiet",
                    tempFile.toString()
            );
            
            Process process = processBuilder.start();
            
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                throw new RuntimeException("Timeout waiting for Semgrep");
            }
            
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            
            // Read stderr for debugging
            String errorOutput;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                errorOutput = reader.lines().collect(Collectors.joining("\n"));
            }

            if (process.exitValue() != 0) {
                 log.error("Semgrep exited non-zero (code: {}). Stderr: {}", process.exitValue(), errorOutput);
                 // Semgrep returns 0 on success (findings or no findings), 1 on error
                 if (output.isEmpty()) {
                     throw new RuntimeException("Semgrep exited with code " + process.exitValue() + ". Error: " + errorOutput);
                 }
            }

            // Parse JSON output
            List<Finding> findings = parseSemgrepOutput(output);
            
            // Check for parse errors in stderr if no findings
            // Semgrep often prints "Ran 0 rules" or "Succeeded" but if there are syntax errors it might be in stderr
            List<String> vulnerabilityStrings = findings.stream()
                .map(f -> String.format("[%s] Line %d: %s", f.getTitle(), f.getStartLine(), f.getDescription()))
                .collect(Collectors.toList());

            if (findings.isEmpty()) {
                log.warn("Semgrep result is empty. Stderr: {}", errorOutput); // Log stderr for debugging zero findings

                if (errorOutput.contains("syntax error") || errorOutput.contains("parse error") || errorOutput.contains("Syntax error")) {
                    String warning = "Semgrep found no vulnerabilities, but hit a syntax parse error during analysis, so the scan may have been interrupted. Please check that the code syntax is correct.";
                    log.warn(warning);
                    vulnerabilityStrings.add("[WARNING] " + warning);
                } else if (errorOutput.contains("No rules run") || errorOutput.contains("unauthorized")) {
                     String warning = "Semgrep did not run any rules — this may be a network issue or a misconfigured ruleset.";
                     log.warn(warning);
                     vulnerabilityStrings.add("[WARNING] " + warning);
                } else {
                     // Generic hint for 0 findings
                     log.info("Semgrep found no issues. Hint: Semgrep static analysis relies on complete data flow; unused variables or serious syntax errors may cause false negatives.");
                }
            } else {
                // print the Semgrep findings to the log
                log.info("========== Semgrep found {} issues ==========", findings.size());
                for (Finding f : findings) {
                    log.info("[{}] Line {}: {}", f.getTitle(), f.getStartLine(), f.getDescription());
                }
                log.info("============================================");
            }

            String resultMsg = "Semgrep analysis complete; found " + findings.size() + " issue(s).";
            if (findings.isEmpty()) {
                resultMsg += " (Note: Semgrep only detects issues in complete code logic; unused variables or syntax errors are ignored.)";
            }
            return new Response(true, vulnerabilityStrings, findings, resultMsg);

        } finally {
            try {
                Files.deleteIfExists(tempFile);
                log.debug("Deleted temp file: {}", tempFile);
            } catch (Exception ignored) {}
        }
    }

    protected List<Finding> parseSemgrepOutput(String jsonOutput) throws Exception {
        JsonNode root = objectMapper.readTree(jsonOutput);
        JsonNode results = root.path("results");
        
        List<Finding> findings = new ArrayList<>();
        if (results.isArray()) {
            log.info("Semgrep raw result count: {}", results.size());
            for (JsonNode result : results) {
                String message = result.path("extra").path("message").asText();
                String ruleId = result.path("check_id").asText();
                int line = result.path("start").path("line").asInt();
                int endLine = result.path("end").path("line").asInt();
                String severityStr = result.path("extra").path("severity").asText("INFO");
                String severity = "MEDIUM";
                if ("ERROR".equalsIgnoreCase(severityStr)) severity = "HIGH";
                else if ("WARNING".equalsIgnoreCase(severityStr)) severity = "MEDIUM";
                else if ("INFO".equalsIgnoreCase(severityStr)) severity = "LOW";
                String category = "SECURITY";
                if (ruleId.contains("correctness") || ruleId.contains("bug")) category = "BUG";
                else if (ruleId.contains("performance")) category = "PERFORMANCE";
                else if (ruleId.contains("maintainability")) category = "MAINTAINABILITY";
                
                Finding finding = Finding.builder()
                        .title(ruleId)
                        .description(message)
                        .startLine(line)
                        .endLine(endLine)
                        .severity(com.codeguardian.enums.SeverityEnum.fromName(severity).getValue())
                        .category(category)
                        .source("Semgrep")
                        .location("Code Snippet") // Will be updated by caller
                        .build();
                        
                findings.add(finding);
            }
        } else {
            log.debug("Semgrep returned no results or an unexpected format");
        }
        return findings;
    }

    protected String resolveSemgrepPath() {
        // 1. Check if 'semgrep' is in PATH
        try {
            Process process = new ProcessBuilder("semgrep", "--version").start();
            if (process.waitFor() == 0) {
                return "semgrep";
            }
        } catch (Exception ignored) {}

        // 2. Check known locations
        String[] knownPaths = {
            "/usr/local/bin/semgrep",
            "/opt/homebrew/bin/semgrep",
            "/Library/Frameworks/Python.framework/Versions/3.8/bin/semgrep", // Mac standard python
            "/Library/Frameworks/Python.framework/Versions/3.9/bin/semgrep",
            "/Library/Frameworks/Python.framework/Versions/3.10/bin/semgrep",
            "/Library/Frameworks/Python.framework/Versions/3.11/bin/semgrep",
            "/Library/Frameworks/Python.framework/Versions/3.12/bin/semgrep",
            System.getProperty("user.home") + "/Library/Python/3.8/bin/semgrep", // User pip install
            System.getProperty("user.home") + "/Library/Python/3.9/bin/semgrep",
            System.getProperty("user.home") + "/.local/bin/semgrep" // Linux standard
        };

        for (String path : knownPaths) {
            if (new java.io.File(path).exists()) {
                log.info("Found Semgrep at: {}", path);
                return path;
            }
        }
        
        return null;
    }

    // Removed the runFallbackAnalysis and checkPattern methods; fallback is no longer supported.
    // private Response runFallbackAnalysis(String code) { ... }
    // private void checkPattern(...) { ... }

    @Data
    @JsonClassDescription("Security analysis request")
    public static class Request {
        @JsonPropertyDescription("The code to analyze")
        @JsonProperty(required = true)
        public String code;
    }

    @Data
    public static class Response {
        public boolean success;
        public List<String> vulnerabilities;
        public List<Finding> findings;
        public String message;

        public Response(boolean success, List<String> vulnerabilities, String message) {
            this(success, vulnerabilities, new ArrayList<>(), message);
        }
        
        public Response(boolean success, List<String> vulnerabilities, List<Finding> findings, String message) {
            this.success = success;
            this.vulnerabilities = vulnerabilities;
            this.findings = findings;
            this.message = message;
        }
    }
}
