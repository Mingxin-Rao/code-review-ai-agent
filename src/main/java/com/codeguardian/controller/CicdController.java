package com.codeguardian.controller;

import com.codeguardian.dto.ReviewRequestDTO;
import com.codeguardian.dto.ReviewResponseDTO;
import com.codeguardian.dto.integration.CicdStatusResponse;
import com.codeguardian.dto.integration.CicdTriggerRequest;
import com.codeguardian.entity.ReviewTask;
import com.codeguardian.repository.ReviewTaskRepository;
import com.codeguardian.service.ReviewService;
import com.codeguardian.service.integration.QualityGateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * CI/CD integration controller
 * Called by Jenkins, GitLab CI, GitHub Actions, and similar tools
 */
@RestController
@RequestMapping("/api/v1/cicd")
@RequiredArgsConstructor
@Slf4j
public class CicdController {

    private final ReviewService reviewService;
    private final ReviewTaskRepository taskRepository;
    private final QualityGateService qualityGateService;

    /**
     * Trigger a review (called by a CI/CD pipeline)
     */
    @PostMapping("/trigger")
    public ResponseEntity<CicdStatusResponse> triggerReview(@RequestBody CicdTriggerRequest request) {
        log.info("Received CI/CD trigger request: {}", request);

        ReviewRequestDTO reviewRequest = ReviewRequestDTO.builder()
                .reviewType("GIT")
                .gitUrl(request.getGitUrl())
                .taskName("CI-" + (request.getTriggerBy() != null ? request.getTriggerBy() : "AUTO") + "-" + System.currentTimeMillis())
                .build();

        // if a project subpath is specified
        if (request.getProjectPath() != null) {
            reviewRequest.setProjectPath(request.getProjectPath());
        }

        // create and start the task
        ReviewResponseDTO taskResponse = reviewService.createReviewTask(reviewRequest);

        // return the task ID immediately; CI tools can poll checkStatus to get the result
        return ResponseEntity.ok(CicdStatusResponse.builder()
                .taskId(taskResponse.getTaskId())
                .status(com.codeguardian.enums.TaskStatusEnum.RUNNING.name())
                .passed(true) // initial status defaults to passed
                .message("Task submitted; please poll the status endpoint")
                .build());
    }

    /**
     * Check review status and results (polled by a CI/CD pipeline)
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<CicdStatusResponse> checkStatus(
            @PathVariable Long taskId,
            @RequestParam(required = false, defaultValue = "CRITICAL") String blockOn) {
        
        Optional<ReviewTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ReviewTask task = taskOpt.get();
        boolean isCompleted = com.codeguardian.enums.TaskStatusEnum.COMPLETED.getValue().equals(task.getStatus());
        boolean isFailed = com.codeguardian.enums.TaskStatusEnum.FAILED.getValue().equals(task.getStatus());
        
        boolean passed = true;
        String message = "Review in progress...";
        CicdStatusResponse.Summary summary = null;

        if (isCompleted) {
            passed = qualityGateService.checkQualityGate(taskId, blockOn);
            message = passed ? "Review passed" : "Review not passed: there are issues at the " + blockOn + " level or above";

            // summary statistics
            ReviewResponseDTO dto = reviewService.getReviewTask(taskId); // Re-use logic to calculate counts
            summary = CicdStatusResponse.Summary.builder()
                    .critical(dto.getCriticalCount())
                    .high(dto.getHighCount())
                    .medium(dto.getMediumCount())
                    .low(dto.getLowCount())
                    .build();
        } else if (isFailed) {
            passed = false;
            message = "Review task execution failed: " + task.getErrorMessage();
        }

        return ResponseEntity.ok(CicdStatusResponse.builder()
                .taskId(task.getId())
                .status(com.codeguardian.enums.TaskStatusEnum.fromValue(task.getStatus()).name())
                .passed(passed)
                .message(message)
                .reportUrl("/review/report/" + task.getId()) // assumed frontend report URL
                .summary(summary)
                .build());
    }
}
