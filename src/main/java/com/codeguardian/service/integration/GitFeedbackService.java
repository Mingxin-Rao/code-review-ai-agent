package com.codeguardian.service.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Git platform feedback service
 * Used to post comments to or update statuses on GitCode
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitFeedbackService {

    @Value("${gitcode.api.base-url:https://api.gitcode.com/api/v5}")
    private String baseUrl;

    @Value("${gitcode.token:}")
    private String token;

    private final RestClient.Builder restClientBuilder;

    public void postComment(String gitUrl, String prNumber, String comment) {
        if (token == null || token.isEmpty()) {
            log.warn("GitCode token not configured; skipping comment posting.");
            log.info("Simulating posting a comment to {} #{}: {}", gitUrl, prNumber, comment);
            return;
        }

        try {
            String projectPath = extractProjectPath(gitUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            // GitLab-compatible) API: POST /projects/:id/merge_requests/:merge_request_iid/notes
            String uri = String.format("/projects/%s/merge_requests/%s/notes", encodedPath, prNumber);

            log.info("Posting a comment to GitCode: {}", uri);
            
            restClientBuilder.baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", comment))
                    .retrieve()
                    .toBodilessEntity();
            
            log.info("Successfully posted a comment to GitCode");
        } catch (Exception e) {
            log.error("Failed to post a comment to GitCode: {}", e.getMessage());
        }
    }

    public void updateStatus(String gitUrl, String commitHash, String state, String description) {
        if (token == null || token.isEmpty()) {
            log.warn("GitCode token not configured; skipping status update.");
            log.info("Simulating a status update to {} {}: {} - {}", gitUrl, commitHash, state, description);
            return;
        }

        try {
            String projectPath = extractProjectPath(gitUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            // GitLab-compatible) API: POST /projects/:id/statuses/:sha
            // state mapping: pending, running, success, failed, canceled
            String gitLabState = mapToGitLabState(state);

            String uri = String.format("/projects/%s/statuses/%s", encodedPath, commitHash);

            log.info("Updating GitCode status: {}", uri);

            restClientBuilder.baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "state", gitLabState,
                            "description", description,
                            "context", "CodeGuardian AI"
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully updated GitCode status");
        } catch (Exception e) {
            log.error("Failed to update GitCode status: {}", e.getMessage());
        }
    }
    
    private String extractProjectPath(String gitUrl) {
        // example: https://gitcode.com/owner/repo.git -> owner/repo
        String cleanUrl = gitUrl.replace(".git", "");
        if (cleanUrl.startsWith("http")) {
            // remove the protocol and domain
            int pathStart = cleanUrl.indexOf("/", cleanUrl.indexOf("://") + 3);
            if (pathStart != -1) {
                return cleanUrl.substring(pathStart + 1);
            }
        }
        return cleanUrl;
    }
    
    private String mapToGitLabState(String state) {
        // map the internal state to a GitLab state
        return switch (state.toLowerCase()) {
            case "success", "passed" -> "success";
            case "failure", "failed", "error" -> "failed";
            case "pending", "running" -> "pending";
            default -> "pending";
        };
    }
}
