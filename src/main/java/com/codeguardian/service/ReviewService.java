package com.codeguardian.service;

import com.codeguardian.dto.FileContentDTO;
import com.codeguardian.dto.ReviewRequestDTO;
import com.codeguardian.dto.ReviewResponseDTO;
import com.codeguardian.entity.Finding;
import com.codeguardian.entity.ReviewTask;
import com.codeguardian.enums.ModelProviderEnum;
import com.codeguardian.repository.FindingRepository;
import com.codeguardian.repository.ReviewReportRepository;
import com.codeguardian.repository.ReviewTaskRepository;
import com.codeguardian.service.cache.SemanticFingerprintCacheService;
import com.codeguardian.service.rules.RuleEngineService;
import com.codeguardian.enums.ReviewTypeEnum;
import com.codeguardian.enums.SeverityEnum;
import com.codeguardian.enums.TaskStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code review service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {
    
    private final ReviewTaskRepository taskRepository;
    private final FindingRepository findingRepository;
    private final ReviewReportRepository reportRepository;
    private final AIModelService aiModelService;
    private final CodeParserService codeParserService;
    private final RuleEngineService ruleEngineService;
    private final SystemConfigService configService;
    private final GitService gitService;
    private final SemanticFingerprintCacheService fingerprintCacheService;
    
    private final ExecutorService executor = Executors.newFixedThreadPool(20);
    private final ExecutorService orchestrationExecutor = Executors.newCachedThreadPool();
    
    @jakarta.annotation.PreDestroy
    public void destroy() {
        log.info("Closing review service executors...");
        executor.shutdown();
        orchestrationExecutor.shutdown();
    }
    
    /**
     * Create a review task and start the review
     */
    @Transactional
    public ReviewResponseDTO createReviewTask(ReviewRequestDTO request) {
        log.info("Creating review task: type={}, scope={}", request.getReviewType(),
                request.getProjectPath() != null ? request.getProjectPath() :
                request.getFilePath() != null ? request.getFilePath() : "Code Snippet");

        // create the task
        ReviewTask task = ReviewTask.builder()
                .name(request.getTaskName() != null ? request.getTaskName() : 
                      generateTaskName(request))
                .reviewType(ReviewTypeEnum.fromName(request.getReviewType()).getValue())
                .scope(determineScope(request))
                .status(TaskStatusEnum.RUNNING.getValue())
                .createdAt(LocalDateTime.now())
                .build();
        
        task = taskRepository.save(task);
        try {
            String type = request.getReviewType() != null ? request.getReviewType().toUpperCase() : "";
            if ("SNIPPET".equals(type)) {
                if (request.getCodeSnippet() != null && !request.getCodeSnippet().isBlank()) {
                    task.setScope(request.getCodeSnippet());
                    task = taskRepository.save(task);
                }
            } else if ("FILE".equals(type)) {
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    FileContentDTO f = request.getFiles().get(0);
                    if (f.getContent() != null) {
                        java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "codeguardian", "uploads", String.valueOf(task.getId()));
                        String rel = f.getPath() != null ? f.getPath().replace('\\','/') : "uploaded.txt";
                        java.nio.file.Path out = root.resolve(rel);
                        java.nio.file.Files.createDirectories(out.getParent());
                        java.nio.file.Files.writeString(out, f.getContent());
                        task.setScope(out.toString());
                        task = taskRepository.save(task);
                    }
                } else if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
                    task.setScope(request.getFilePath());
                    task = taskRepository.save(task);
                }
            } else if ("GIT".equals(type)) {
                if (request.getGitUrl() != null && !request.getGitUrl().isBlank()) {
                    task.setScope(request.getGitUrl());
                    task = taskRepository.save(task);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save the code sample; falling back to the default scope label", e);
        }
        
        ReviewTask finalTask = task;
        Long taskId = task.getId();
        Runnable reviewJob = () -> runReviewAsync(taskId, finalTask, request);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    orchestrationExecutor.submit(reviewJob);
                }
            });
        } else {
            orchestrationExecutor.submit(reviewJob);
        }
        
        return buildResponseDTO(finalTask);
    }
    
    private void runReviewAsync(Long taskId, ReviewTask fallbackTask, ReviewRequestDTO request) {
        if (taskId == null) {
            log.error("Review task execution failed: taskId is null");
            return;
        }

        ReviewTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            task = fallbackTask;
        }
        if (task == null || task.getId() == null || !taskId.equals(task.getId())) {
            log.error("Review task execution failed: taskId={} does not exist", taskId);
            return;
        }

        try {
            performReview(task, request);
            task.setStatus(TaskStatusEnum.COMPLETED.getValue());
            task.setCompletedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Review task execution failed: taskId={}", task.getId(), e);
            task.setStatus(TaskStatusEnum.FAILED.getValue());
            task.setErrorMessage(e.getMessage());
        } finally {
            taskRepository.save(task);
        }
    }

    /**
     * Perform the review
     */
    private void performReview(ReviewTask task, ReviewRequestDTO request) {
        String type = request.getReviewType().toUpperCase();
        
        if ("DIRECTORY".equals(type) || "PROJECT".equals(type)) {
            performParallelReview(task, request);
        } else if ("GIT".equals(type)) {
            if (request.getProjectPath() == null && request.getGitUrl() != null) {
                try {
                    log.info("Git repository not cloned yet; starting clone: {}", request.getGitUrl());
                    String localPath = gitService.cloneRepository(request.getGitUrl(), request.getGitUsername(), request.getGitPassword());
                    request.setProjectPath(localPath);
                    task.setScope(localPath);
                    taskRepository.save(task); // update the task scope
                    log.info("Git clone finished, local path: {}", localPath);
                } catch (Exception e) {
                    log.error("Git auto-clone failed", e);
                    throw new RuntimeException("Git clone failed: " + e.getMessage());
                }
            }
            
            if (request.getProjectPath() != null) {
                performParallelReview(task, request);
            } else {
                throw new UnsupportedOperationException("Git repository is not cloned or the path is empty");
            }
        } else {
            String codeContent = fetchCodeContent(request);
            if (codeContent == null || codeContent.trim().isEmpty()) {
                throw new IllegalArgumentException("Code content is empty");
            }
            List<Finding> findings = executeReviewStrategy(codeContent, request.getLanguage(), request);
            saveFindings(task, findings);
            log.info("Review completed: taskId={}, findingsCount={}", task.getId(), findings.size());
        }
    }

    /**
     * Run the review in parallel using a thread pool
     */
    private void performParallelReview(ReviewTask task, ReviewRequestDTO request) {
        List<Future<List<Finding>>> futures;

        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            // Source A: directly uploaded file list
            log.info("Reviewing with the uploaded file list: taskId={}, count={}", task.getId(), request.getFiles().size());
            futures = request.getFiles().stream()
                .map(file -> executor.submit(() -> reviewSingleFile(file.getPath(), file.getContent(), request)))
                .collect(Collectors.toList());
        } else {
            // Source B: scan a local directory
            String path = request.getProjectPath();
            if (path == null || path.trim().isEmpty()) {
                path = request.getDirectoryPath();
            }
            
            if (path == null || path.trim().isEmpty()) {
                 throw new IllegalArgumentException("Project or directory path must not be empty");
            }
    
            // read the configured scope
            String includePaths = configService.getSettings().getIncludePaths();
            String excludePaths = configService.getSettings().getExcludePaths();

            List<Path> files = codeParserService.scanDirectory(path, includePaths, excludePaths);
            log.info("Starting parallel review of local directory: taskId={}, path={}, fileCount={}", task.getId(), path, files.size());
    
            final Path rootPath = Paths.get(path).toAbsolutePath().normalize();

            futures = files.stream()
                .map(filePath -> executor.submit(() -> {
                    try {
                        String content = codeParserService.readFile(filePath.toString());
                        String relativePath = rootPath.relativize(filePath.toAbsolutePath().normalize()).toString();
                        return reviewSingleFile(relativePath, content, request);
                    } catch (Exception e) {
                        log.error("Failed to read file: {}", filePath, e);
                        return new ArrayList<Finding>();
                    }
                }))
                .collect(Collectors.toList());
        }

        List<Finding> allFindings = new ArrayList<>();
        for (Future<List<Finding>> future : futures) {
            try {
                List<Finding> findings = future.get();
                if (findings != null) {
                    allFindings.addAll(findings);
                }
            } catch (Exception e) {
                log.error("Failed to get review results", e);
            }
        }
        
        saveFindings(task, allFindings);
        log.info("Parallel review completed: taskId={}, findingsCount={}", task.getId(), allFindings.size());
    }

    private List<Finding> reviewSingleFile(String relativePath, String content, ReviewRequestDTO request) {
        try {
            String language = detectLanguage(relativePath);
            List<Finding> fileFindings = executeReviewStrategy(content, language, request);
            
            fileFindings.forEach(f -> {
                f.setLocation(relativePath + ": " + f.getLocation());
            });
            
            return fileFindings;
        } catch (Exception e) {
            log.error("File review failed: {}", relativePath, e);
            return new ArrayList<>();
        }
    }
    
    private void saveFindings(ReviewTask task, List<Finding> findings) {
        findings.forEach(finding -> {
            finding.setTaskId(task.getId());
            findingRepository.save(finding);
        });
    }
    
    private String detectLanguage(String pathStr) {
        String fileName = Paths.get(pathStr).getFileName().toString().toLowerCase();
        if (fileName.endsWith(".java")) return "Java";
        if (fileName.endsWith(".py")) return "Python";
        if (fileName.endsWith(".js")) return "JavaScript";
        if (fileName.endsWith(".ts")) return "TypeScript";
        if (fileName.endsWith(".go")) return "Go";
        if (fileName.endsWith(".rs")) return "Rust";
        if (fileName.endsWith(".cpp") || fileName.endsWith(".c") || fileName.endsWith(".h")) return "C/C++";
        return "Unknown";
    }

    private String detectLanguage(Path path) {
        return detectLanguage(path.toString());
    }

    /**
     * Fetch the code content
     */
    private String fetchCodeContent(ReviewRequestDTO request) {
        return switch (request.getReviewType().toUpperCase()) {
            case "SNIPPET" -> request.getCodeSnippet();
            case "FILE" -> {
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    yield request.getFiles().get(0).getContent();
                }
                yield codeParserService.readFile(request.getFilePath());
            }
            default -> throw new IllegalArgumentException("Unsupported review type: " + request.getReviewType());
        };
    }

    /**
     * Execute the review strategy (rule engine or AI model)
     */
    private List<Finding> executeReviewStrategy(String codeContent, String language, ReviewRequestDTO request) {
        boolean useRulesOnly = Boolean.TRUE.equals(request.getRulesOnly());
        List<Finding> findings;
        
        if (useRulesOnly) {
            if ("CUSTOM".equalsIgnoreCase(request.getRuleTemplate())) {
                findings = ruleEngineService.reviewWithCustom(codeContent, request.getCustomRules());
            } else {
                findings = ruleEngineService.reviewWithTemplate(codeContent, language, request.getRuleTemplate());
            }
            // manually tag the source in rule-engine mode
            if (findings != null) {
                findings.forEach(f -> f.setSource("RuleEngine"));
            }
        } else {
            boolean enableRag = request.getEnableRag() != null ? request.getEnableRag() : true;
            String requestedProvider = request.getModelProvider();
            ModelProviderEnum resolvedProvider = ModelProviderEnum.from(requestedProvider).orElse(null);
            if (resolvedProvider == null) {
                List<ModelProviderEnum> available = aiModelService.getAvailableProviders();
                if (available != null && !available.isEmpty()) {
                    resolvedProvider = available.get(0);
                }
            }

            final ModelProviderEnum provider = resolvedProvider;
            final String providerNameForAi = provider != null ? provider.name() : requestedProvider;
            final int blockStartLine = 1;
            findings = fingerprintCacheService
                    .tryGetCachedFindings(codeContent, language, provider, enableRag, blockStartLine)
                    .orElseGet(() -> {
                        List<Finding> fresh = aiModelService.reviewCode(
                                codeContent,
                                language,
                                providerNameForAi,
                                enableRag
                        );
                        fingerprintCacheService.storeFindings(codeContent, language, provider, enableRag, blockStartLine, fresh);
                        return fresh;
                    });
        }
        
        // filter issues according to system configuration
        try {
            Map<String, Boolean> categories = configService.getSettings().getRuleCategories();
            if (categories != null && !categories.isEmpty()) {
                findings = findings.stream()
                        .filter(f -> isCategoryEnabled(f.getCategory(), categories))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to filter review results; returning the original results", e);
        }
        
        return findings;
    }

    private boolean isDuplicate(Finding newFinding, List<Finding> existingFindings) {
        if (existingFindings == null || existingFindings.isEmpty()) return false;
        for (Finding existing : existingFindings) {
            if (existing.getStartLine() == newFinding.getStartLine()) {
                if (existing.getCategory() != null && newFinding.getCategory() != null && 
                    existing.getCategory().equals(newFinding.getCategory())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isCategoryEnabled(String findingCategory, Map<String, Boolean> configCategories) {
        if (findingCategory == null) return true;
        String code = findingCategory;
        String key = "style";
        if ("SECURITY".equals(code)) key = "security";
        else if ("PERFORMANCE".equals(code)) key = "performance";
        else if ("BUG".equals(code)) key = "logic_error";
        else if ("MAINTAINABILITY".equals(code)) key = "maintainability";
        return configCategories.getOrDefault(key, true);
    }
    
    /**
     * Get review task details
     */
    public ReviewResponseDTO getReviewTask(Long taskId) {
        ReviewTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task does not exist: " + taskId));
        
        return buildResponseDTO(task);
    }
    
    /**
     * Build the response DTO
     */
    private ReviewResponseDTO buildResponseDTO(ReviewTask task) {
        List<Finding> findings = findingRepository.findByTaskId(task.getId());
        if (findings == null) findings = List.of();
        
        // use the Stream API to count issues by severity in one pass
        Map<Integer, Integer> severityCounts = findings.stream()
                .filter(f -> f.getSeverity() != null)
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.summingInt(e -> 1)));

        return ReviewResponseDTO.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .status(TaskStatusEnum.fromValue(task.getStatus()).name())
                .reviewType(ReviewTypeEnum.fromValue(task.getReviewType()).name())
                .scope(task.getScope())
                .createdAt(task.getCreatedAt())
                .totalFindings(findings.size())
                .criticalCount(severityCounts.getOrDefault(SeverityEnum.CRITICAL.getValue(), 0))
                .highCount(severityCounts.getOrDefault(SeverityEnum.HIGH.getValue(), 0))
                .mediumCount(severityCounts.getOrDefault(SeverityEnum.MEDIUM.getValue(), 0))
                .lowCount(severityCounts.getOrDefault(SeverityEnum.LOW.getValue(), 0))
                .build();
    }
    
    /**
     * Generate the task name
     *
     * <p>For PROJECT/DIRECTORY/FILE/GIT types the path or repository name is preferred, so the dashboard shows a meaningful name.<br/>
     * For Project/Directory/File/Git types, prefer the path or repository name so the dashboard shows a clear name.</p>
     *
     * @param request the review request
     * @return the task name
     */
    private String generateTaskName(ReviewRequestDTO request) {
        String type = request.getReviewType() != null ? request.getReviewType().toUpperCase() : "";
        switch (type) {
            case "PROJECT": {
                String base = extractBaseName(request.getProjectPath());
                if (base != null && !base.isEmpty()) return base;
                break;
            }
            case "DIRECTORY": {
                String dirPath = request.getDirectoryPath();
                if (dirPath != null && !dirPath.isBlank()) {
                    String normalized = dirPath.replace('\\', '/');
                    if (normalized.contains("/")) return normalized;
                }
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    List<String> dirs = request.getFiles().stream()
                            .map(f -> f.getPath())
                            .filter(p -> p != null && !p.isBlank())
                            .map(p -> p.replace('\\', '/'))
                            .map(p -> {
                                int last = p.lastIndexOf('/');
                                return last >= 0 ? p.substring(0, last) : "";
                            })
                            .collect(Collectors.toList());
                    String common = computeCommonDir(dirs);
                    if (common != null && !common.isEmpty()) return common;
                }
                if (dirPath != null && !dirPath.isBlank()) return dirPath;
                break;
            }
            case "FILE": {
                String base = extractBaseName(request.getFilePath());
                if (base != null && !base.isEmpty()) return base;
                break;
            }
            case "SNIPPET": {
                String identifier = guessSnippetDisplayName(request.getCodeSnippet(), request.getLanguage());
                if (identifier != null && !identifier.isEmpty()) {
                    return identifier;
                }
                break;
            }
            case "GIT": {
                if (request.getProjectPath() != null && !request.getProjectPath().trim().isEmpty()) {
                    String base = extractBaseName(request.getProjectPath());
                    if (base != null && !base.isEmpty()) return base;
                }
                String repo = extractRepoNameFromUrl(request.getGitUrl());
                if (repo != null && !repo.isEmpty()) return repo;
                if (request.getGitUrl() != null && !request.getGitUrl().isBlank()) {
                    return request.getGitUrl();
                }
                break;
            }
            default:
                break;
        }

        String prefix = switch (type) {
            case "PROJECT" -> "Project Review";
            case "DIRECTORY" -> "Directory Review";
            case "FILE" -> "File Review";
            case "SNIPPET" -> "Code Snippet Review";
            case "GIT" -> "Git Repository Review";
            default -> "Code Review";
        };
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String computeCommonDir(List<String> paths) {
        if (paths == null || paths.isEmpty()) return null;
        List<String[]> segments = paths.stream()
                .map(p -> p.split("/"))
                .collect(Collectors.toList());
        int minLen = segments.stream().mapToInt(a -> a.length).min().orElse(0);
        if (minLen == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < minLen; i++) {
            String seg = segments.get(0)[i];
            boolean allSame = true;
            for (int j = 1; j < segments.size(); j++) {
                if (!seg.equals(segments.get(j)[i])) { allSame = false; break; }
            }
            if (!allSame) break;
            if (sb.length() > 0) sb.append('/');
            sb.append(seg);
        }
        return sb.toString();
    }

    /**
     * Extract the last segment of a path
     *
     * @param path the path
     * @return the last segment (directory or file name), or null if the path is empty
     */
    private String extractBaseName(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            return Paths.get(path).getFileName().toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String guessSnippetDisplayName(String code, String language) {
        if (code == null || code.trim().isEmpty()) return null;
        String lang = language != null ? language.toLowerCase() : "";
        if (lang.contains("java")) {
            Pattern classPat = Pattern.compile("\\b(class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");
            Matcher cm = classPat.matcher(code);
            String className = cm.find() ? cm.group(2) : null;
            Pattern methodPat = Pattern.compile("\\b(?:public|protected|private|static|final|synchronized|abstract|native|transient|\\s)+\\s*[\\w<>\\[\\]]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
            Matcher mm = methodPat.matcher(code);
            String methodName = mm.find() ? mm.group(1) : null;
            if (methodName != null) return className != null ? className + "." + methodName : methodName;
            if (className != null) return className;
            return null;
        } else if (lang.contains("python")) {
            Pattern classPat = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
            Matcher cm = classPat.matcher(code);
            String className = cm.find() ? cm.group(1) : null;
            Pattern methodPat = Pattern.compile("\\bdef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
            Matcher mm = methodPat.matcher(code);
            String methodName = mm.find() ? mm.group(1) : null;
            if (methodName != null) return className != null ? className + "." + methodName : methodName;
            if (className != null) return className;
            return null;
        } else if (lang.contains("typescript") || lang.contains("ts") || lang.contains("javascript") || lang.contains("js")) {
            Pattern classPat = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
            Matcher cm = classPat.matcher(code);
            String className = cm.find() ? cm.group(1) : null;
            Pattern funcPat1 = Pattern.compile("\\bfunction\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
            Matcher m1 = funcPat1.matcher(code);
            String methodName = m1.find() ? m1.group(1) : null;
            if (methodName == null) {
                Pattern funcPat2 = Pattern.compile("\\bconst\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\(");
                Matcher m2 = funcPat2.matcher(code);
                methodName = m2.find() ? m2.group(1) : null;
            }
            if (methodName != null) return className != null ? className + "." + methodName : methodName;
            if (className != null) return className;
            return null;
        }
        Pattern genericClass = Pattern.compile("\\b(class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Matcher gc = genericClass.matcher(code);
        if (gc.find()) return gc.group(2);
        Pattern genericFunc = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
        Matcher gf = genericFunc.matcher(code);
        return gf.find() ? gf.group(1) : null;
    }

    /**
     * Parse the repository name from a Git repository URL
     *
     * @param url the repository URL
     * @return the repository name (without the .git suffix), or null if parsing fails
     */
    private String extractRepoNameFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            String trimmed = url.trim();
            if (trimmed.endsWith(".git")) trimmed = trimmed.substring(0, trimmed.length() - 4);
            int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
            if (slash >= 0 && slash < trimmed.length() - 1) {
                return trimmed.substring(slash + 1);
            }
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }
    
    private String determineScope(ReviewRequestDTO request) {
        String type = request.getReviewType() != null ? request.getReviewType().toUpperCase() : "UNKNOWN";
        switch (type) {
            case "PROJECT":
                return "Whole Project";
            case "DIRECTORY":
                return "Directory";
            case "FILE":
                return "File";
            case "GIT":
                return "Git Repository";
            case "SNIPPET":
                return "Code Snippet";
            default:
                return "Code Snippet";
        }
    }

    /**
     * Delete a review task and its related data
     * Deletes in foreign-key dependency order: findings -> review_reports -> review_task
     *
     * @param taskId the task ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTaskWithRelatedData(Long taskId) {
        log.info("Starting deletion of task and related data: taskId={}", taskId);

        // delete findings
        long findingsCount = findingRepository.countByTaskId(taskId);
        if (findingsCount > 0) {
            findingRepository.deleteByTaskId(taskId);
            log.info("Deleted {} findings: taskId={}", findingsCount, taskId);
        }

        // delete the review report
        reportRepository.findByTaskId(taskId).ifPresent(report -> {
            reportRepository.delete(report);
            log.info("Deleted review report: taskId={}", taskId);
        });

        // delete the task
        taskRepository.findById(taskId).ifPresent(task -> {
            taskRepository.delete(task);
            log.info("Deleted task: taskId={}, taskName={}", taskId, task.getName());
        });

        log.info("Task and related data deletion completed: taskId={}", taskId);
    }
}
