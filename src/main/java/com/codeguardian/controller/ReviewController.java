package com.codeguardian.controller;

import com.codeguardian.dto.FindingDTO;
import com.codeguardian.dto.GitCloneResponseDTO;
import com.codeguardian.dto.GitFileResponseDTO;
import com.codeguardian.dto.ReviewRequestDTO;
import com.codeguardian.dto.ReviewResponseDTO;
import com.codeguardian.entity.Finding;
import com.codeguardian.entity.ReviewTask;
import com.codeguardian.repository.FindingRepository;
import com.codeguardian.repository.ReviewReportRepository;
import com.codeguardian.repository.ReviewTaskRepository;
import com.codeguardian.service.ReviewService;
import com.codeguardian.service.GitService;
import com.codeguardian.service.SystemConfigService;
import com.codeguardian.enums.ReviewTypeEnum;
import com.codeguardian.enums.SeverityEnum;
import com.codeguardian.enums.TaskStatusEnum;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.annotation.SaCheckPermission;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Code-review controller.
 * Code review controller.
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {
    
    private final ReviewService reviewService;
    private final ReviewTaskRepository taskRepository;
    private final FindingRepository findingRepository;
    private final ReviewReportRepository reportRepository;
    private final GitService gitService;
    private final SystemConfigService configService;
    
    /**
     * Review a code snippet.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/snippet")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<ReviewResponseDTO> reviewSnippet(@Valid @RequestBody ReviewRequestDTO request) {
        request.setReviewType("SNIPPET");
        ReviewResponseDTO response = reviewService.createReviewTask(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Review a single file.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/file")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<ReviewResponseDTO> reviewFile(@Valid @RequestBody ReviewRequestDTO request) {
        request.setReviewType("FILE");
        ReviewResponseDTO response = reviewService.createReviewTask(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Review a directory.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/directory")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<ReviewResponseDTO> reviewDirectory(@Valid @RequestBody ReviewRequestDTO request) {
        request.setReviewType("DIRECTORY");
        ReviewResponseDTO response = reviewService.createReviewTask(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Review the whole project.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/project")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<ReviewResponseDTO> reviewProject(@Valid @RequestBody ReviewRequestDTO request) {
        request.setReviewType("PROJECT");
        ReviewResponseDTO response = reviewService.createReviewTask(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Review a Git repository.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/git")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<ReviewResponseDTO> reviewGitProject(@Valid @RequestBody ReviewRequestDTO request) {
        request.setReviewType("GIT");
        ReviewResponseDTO response = reviewService.createReviewTask(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clone a Git project and return its file list (used by the front end to render the file tree).
     * Clone a Git repository and return its file list (for the frontend file tree).
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @PostMapping("/git/clone")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<GitCloneResponseDTO> cloneGitRepository(@RequestBody ReviewRequestDTO request) {
        try {
            String gitUrl = request.getGitUrl();
            String username = request.getGitUsername();
            String password = request.getGitPassword();
            
            if (gitUrl == null || gitUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(GitCloneResponseDTO.builder()
                        .success(false)
                        .error("The Git repository URL must not be empty")
                        .build());
            }

            // clone the repository
            String localPath = gitService.cloneRepository(gitUrl, username, password);

            // get the configured scope
            String includePaths = configService.getSettings().getIncludePaths();
            String excludePaths = configService.getSettings().getExcludePaths();

            // get the file list
            List<String> fileList = gitService.getFileList(localPath, includePaths, excludePaths);
            
            return ResponseEntity.ok(GitCloneResponseDTO.builder()
                    .localPath(localPath)
                    .fileList(fileList)
                    .success(true)
                    .build());
        } catch (Exception e) {
            log.error("Failed to clone the Git repository", e);
            return ResponseEntity.status(500).body(GitCloneResponseDTO.builder()
                    .success(false)
                    .error("Failed to clone the Git repository: " + e.getMessage())
                    .build());
        }
    }
    
    /**
     * Read a file from a cloned Git repository.
     *
     * <p>Requires the `QUERY` permission.</p>
     */
    @GetMapping("/git/file")
    @SaCheckPermission("QUERY")
    public ResponseEntity<GitFileResponseDTO> readGitFile(@RequestParam("path") String filePath) {
        try {
            // filePath is the full path (basePath + relativePath)
            String content = gitService.readFile(filePath);

            return ResponseEntity.ok(GitFileResponseDTO.builder()
                    .content(content)
                    .success(true)
                    .build());
        } catch (Exception e) {
            log.error("Failed to read the Git file", e);
            return ResponseEntity.status(500).body(GitFileResponseDTO.builder()
                    .success(false)
                    .error("Failed to read the file: " + e.getMessage())
                    .build());
        }
    }

    /**
     * List the files under the server-side configured project root.
     * List files under the server-configured project root.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @GetMapping("/server/list")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<GitCloneResponseDTO> getServerFileList() {
        try {
            String projectRoot = configService.getSettings().getProjectRoot();
            
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(GitCloneResponseDTO.builder()
                        .success(false)
                        .error("No project root is configured")
                        .build());
            }

            // get the configured scope
            String includePaths = configService.getSettings().getIncludePaths();
            String excludePaths = configService.getSettings().getExcludePaths();

            // get the file list
            List<String> fileList = gitService.getFileList(projectRoot, includePaths, excludePaths);
            
            return ResponseEntity.ok(GitCloneResponseDTO.builder()
                    .localPath(projectRoot)
                    .fileList(fileList)
                    .success(true)
                    .build());
        } catch (Exception e) {
            log.error("Failed to list server files", e);
            return ResponseEntity.status(500).body(GitCloneResponseDTO.builder()
                    .success(false)
                    .error("Failed to get the file list: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Read a file from the server.
     *
     * <p>Requires the `REVIEW` permission.</p>
     */
    @GetMapping("/server/file")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<GitFileResponseDTO> readServerFile(@RequestParam("path") String relativePath) {
        try {
            String projectRoot = configService.getSettings().getProjectRoot();
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                throw new IllegalArgumentException("No project root is configured");
            }

            // Resolve the full path, then normalize so that any "../" segments in the
            // caller-supplied relativePath collapse before the containment check below.
            java.nio.file.Path rootPath = java.nio.file.Paths.get(projectRoot);
            java.nio.file.Path filePath = rootPath.resolve(relativePath).normalize();

            // ensure the file stays under projectRoot
            if (!filePath.startsWith(rootPath)) {
                 throw new IllegalArgumentException("Invalid file path");
            }

            String content = gitService.readFile(filePath.toString());

            return ResponseEntity.ok(GitFileResponseDTO.builder()
                    .content(content)
                    .success(true)
                    .build());
        } catch (Exception e) {
            log.error("Failed to read the server file", e);
            return ResponseEntity.status(500).body(GitFileResponseDTO.builder()
                    .success(false)
                    .error("Failed to read the file: " + e.getMessage())
                    .build());
        }
    }
    
    /**
     * Get review task details.
     *
     * <p>Requires the `QUERY` permission.</p>
     */
    @GetMapping("/task/{taskId}")
    @SaCheckPermission("QUERY")
    public ResponseEntity<ReviewResponseDTO> getTask(@PathVariable("taskId") Long taskId) {
        ReviewResponseDTO response = reviewService.getReviewTask(taskId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the findings of a review task.
     *
     * <p>Requires the `QUERY` permission.</p>
     */
    @GetMapping("/task/{taskId}/findings")
    @SaCheckPermission("QUERY")
    public ResponseEntity<List<FindingDTO>> getFindings(@PathVariable("taskId") Long taskId) {
        List<Finding> findings = findingRepository.findByTaskId(taskId);
        
        // read the max-issues-to-display setting
        Integer maxIssues = configService.getSettings().getMaxIssues();
        if (maxIssues != null && maxIssues > 0 && findings.size() > maxIssues) {
            findings = findings.subList(0, maxIssues);
        }
        
        List<FindingDTO> dtos = findings.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Query review history.
     *
     * <p>Requires the `QUERY` permission.</p>
     */
    @GetMapping("/history")
    @SaCheckPermission("QUERY")
    public ResponseEntity<Page<ReviewResponseDTO>> getHistory(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "reviewType", required = false) String reviewType,
            @RequestParam(value = "startTime", required = false) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) LocalDateTime endTime,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : 
                Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Integer reviewTypeCode = reviewType != null && !reviewType.isEmpty() ? ReviewTypeEnum.fromName(reviewType).getValue() : null;
        Page<ReviewTask> tasks = taskRepository.findByConditions(
                name, reviewTypeCode, startTime, endTime, pageable);
        
        Page<ReviewResponseDTO> response = tasks.map(task -> {
            List<Finding> findings = findingRepository.findByTaskId(task.getId());
            return ReviewResponseDTO.builder()
                .taskId(task.getId())
                .taskName(ReviewTypeEnum.fromValue(task.getReviewType()) == ReviewTypeEnum.GIT && task.getScope() != null ? task.getScope() : task.getName())
                .status(TaskStatusEnum.fromValue(task.getStatus()).name())
                .reviewType(ReviewTypeEnum.fromValue(task.getReviewType()).name())
                .scope(mapScopeLabelByType(task.getReviewType()))
                .createdAt(task.getCreatedAt())
                .totalFindings(findings != null ? findings.size() : 0)
                .criticalCount(countBySeverity(findings, SeverityEnum.CRITICAL.getValue()))
                .highCount(countBySeverity(findings, SeverityEnum.HIGH.getValue()))
                .mediumCount(countBySeverity(findings, SeverityEnum.MEDIUM.getValue()))
                .lowCount(countBySeverity(findings, SeverityEnum.LOW.getValue()))
                .build();
        });
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/task/{taskId}")
    @SaCheckPermission("REVIEW")
    public ResponseEntity<Void> deleteTask(@PathVariable("taskId") Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            return ResponseEntity.notFound().build();
        }

        reviewService.deleteTaskWithRelatedData(taskId);
        return ResponseEntity.ok().build();
    }
    
    private FindingDTO convertToDTO(Finding finding) {
        return FindingDTO.builder()
                .id(finding.getId())
                .severity(com.codeguardian.enums.SeverityEnum.fromValue(finding.getSeverity()).name())
                .title(finding.getTitle())
                .location(finding.getLocation())
                .startLine(finding.getStartLine())
                .endLine(finding.getEndLine())
                .description(finding.getDescription())
                .suggestion(finding.getSuggestion())
                .diff(finding.getDiff())
                .category(finding.getCategory())
                .build();
    }
    
    private int countBySeverity(List<Finding> findings, Integer severity) {
        if (findings == null) return 0;
        return (int) findings.stream()
                .filter(f -> severity.equals(f.getSeverity()))
                .count();
    }
    
    private String mapScopeLabelByType(Integer reviewType) {
        ReviewTypeEnum e = ReviewTypeEnum.fromValue(reviewType);
        if (e == ReviewTypeEnum.PROJECT) return "Whole Project";
        if (e == ReviewTypeEnum.DIRECTORY) return "Directory";
        if (e == ReviewTypeEnum.FILE) return "File";
        if (e == ReviewTypeEnum.GIT) return "Git Repository";
        return "Code Snippet";
    }
}
