package com.codeguardian.controller;

import com.codeguardian.dto.ReviewRequestDTO;
import com.codeguardian.dto.ReviewResponseDTO;
import com.codeguardian.dto.webhook.GitHubWebhookPayload;
import com.codeguardian.service.ReviewService;
import com.codeguardian.service.integration.GitFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Webhook controller
 * Handles webhook events from GitHub/GitLab
 */
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ReviewService reviewService;
    private final GitFeedbackService gitFeedbackService;

    /**
     * Handle GitCode (GitLab-compatible) webhooks
     * Events of interest: Merge Request Hook
     */
    @PostMapping("/gitcode")
    public ResponseEntity<String> handleGitCodeWebhook(
            @RequestHeader(value = "X-Gitlab-Event", defaultValue = "") String eventType,
            @RequestBody Map<String, Object> payload) {
        
        log.info("Received GitCode webhook event: {}", eventType);

        String objectKind = (String) payload.get("object_kind");
        if (!"merge_request".equals(objectKind)) {
            return ResponseEntity.ok("Ignored event type: " + objectKind);
        }

        Map<String, Object> attributes = (Map<String, Object>) payload.get("object_attributes");
        if (attributes == null) {
            return ResponseEntity.badRequest().body("Invalid request body: missing object_attributes");
        }

        String action = (String) attributes.get("action");
        if (!"open".equals(action) && !"update".equals(action) && !"reopen".equals(action)) {
            return ResponseEntity.ok("Ignored action: " + action);
        }

        // process asynchronously
        CompletableFuture.runAsync(() -> processGitCodeMr(payload));

        return ResponseEntity.ok("Webhook received; processing has started.");
    }

    private void processGitCodeMr(Map<String, Object> payload) {
        try {
            Map<String, Object> attributes = (Map<String, Object>) payload.get("object_attributes");
            Map<String, Object> project = (Map<String, Object>) payload.get("project");
            
            if (attributes == null || project == null) return;

            String gitUrl = (String) project.get("git_http_url");
            String htmlUrl = (String) project.get("web_url");
            String branch = (String) attributes.get("source_branch");
            
            Map<String, Object> lastCommit = (Map<String, Object>) attributes.get("last_commit");
            String commitSha = lastCommit != null ? (String) lastCommit.get("id") : null;
            
            Integer mrIid = (Integer) attributes.get("iid");
            String mrTitle = (String) attributes.get("title");

            log.info("Processing GitCode MR: {}/merge_requests/{} (branch: {}, commit: {})", htmlUrl, mrIid, branch, commitSha);

            // set commit status to Pending
            if (commitSha != null) {
                gitFeedbackService.updateStatus(gitUrl, commitSha, "pending", "CodeGuardian AI is reviewing...");
            }

            // trigger the review
            ReviewRequestDTO request = ReviewRequestDTO.builder()
                    .reviewType("GIT")
                    .gitUrl(gitUrl)
                    .taskName("MR-" + mrIid + "-" + mrTitle)
                    .build();

            ReviewResponseDTO response = reviewService.createReviewTask(request);

            // post the initial comment
            gitFeedbackService.postComment(gitUrl, String.valueOf(mrIid),
                    "🤖 **CodeGuardian AI** has started reviewing this MR.\n\n" +
                    "Task ID: `" + response.getTaskId() + "`\n" +
                    "Please wait for the review report.");

        } catch (Exception e) {
            log.error("Failed to process GitCode webhook", e);
        }
    }
}
