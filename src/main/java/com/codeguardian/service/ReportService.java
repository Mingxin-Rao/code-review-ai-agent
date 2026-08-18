package com.codeguardian.service;

import com.codeguardian.entity.Finding;
import com.codeguardian.entity.ReviewReport;
import com.codeguardian.entity.ReviewTask;
import com.codeguardian.repository.FindingRepository;
import com.codeguardian.repository.ReviewReportRepository;
import com.codeguardian.repository.ReviewTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Report generation service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    
    private final ReviewTaskRepository taskRepository;
    private final ReviewReportRepository reportRepository;
    private final FindingRepository findingRepository;
    private final CodeParserService codeParserService;
    private final SystemConfigService systemConfigService;
    private static final Pattern MD_PREFIX = Pattern.compile("^\\s*#{1,6}(?:[:\\s]*)");
    
    /**
     * Generate the review report
     */
    public ReviewReport generateReport(Long taskId) {
        ReviewTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        if (!com.codeguardian.enums.TaskStatusEnum.COMPLETED.getValue().equals(task.getStatus())) {
            throw new IllegalStateException("Task not completed; cannot generate report: " + taskId);
        }

        ReviewReport existingReport = reportRepository.findByTaskId(taskId).orElse(null);
        if (existingReport != null) {
            log.info("Report already exists; returning existing report: taskId={}", taskId);
            return existingReport;
        }

        return createNewReport(taskId, task);
    }

    /**
     * Force regeneration of the review report (used when the latest format is required, e.g. for PDF generation)
     */
    @Transactional(rollbackFor = Exception.class)
    public ReviewReport generateReportForceRefresh(Long taskId) {
        ReviewTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        if (!com.codeguardian.enums.TaskStatusEnum.COMPLETED.getValue().equals(task.getStatus())) {
            throw new IllegalStateException("Task not completed; cannot generate report: " + taskId);
        }

        // Delete the existing report to force regeneration
        reportRepository.findByTaskId(taskId).ifPresent(report -> {
            log.info("Deleting old report to force refresh: taskId={}, reportId={}", taskId, report.getId());
            reportRepository.deleteById(report.getId());
            reportRepository.flush(); // Execute the delete immediately
        });

        return createNewReport(taskId, task);
    }

    /**
     * Create a new report
     */
    private ReviewReport createNewReport(Long taskId, ReviewTask task) {
        List<Finding> findings = findingRepository.findByTaskId(taskId);
        int maxIssues = systemConfigService.getSettings().getMaxIssues();

        String markdownContent = generateMarkdownReport(task, findings, maxIssues);
        String htmlContent = generateHTMLReport(task, findings, maxIssues);
        String statistics = generateStatistics(findings);

        ReviewReport report = ReviewReport.builder()
                .taskId(task.getId())
                .markdownContent(markdownContent)
                .htmlContent(htmlContent)
                .statistics(statistics)
                .build();

        return reportRepository.save(report);
    }
    
    /**
     * Generate the Markdown report
     */
    private String generateMarkdownReport(ReviewTask task, List<Finding> findings, int maxIssues) {
        StringBuilder report = new StringBuilder();
        report.append("# Code Review Report\n\n");
        report.append("**Task Name**: ").append(task.getName()).append("\n\n");
        report.append("**Review Type**: ").append(reviewTypeLabel(task.getReviewType())).append("\n\n");
        report.append("**Review Scope**: ").append(reviewTypeLabel(task.getReviewType())).append("\n\n");
        String createdTime = task.getCreatedAt() != null ? TIME_FORMATTER.format(task.getCreatedAt()) : "";
        report.append("**Created**: ").append(createdTime).append("\n\n");
        report.append("**Total Issues**: ").append(findings != null ? findings.size() : 0).append("\n\n");
        
        report.append(generateStatisticsMarkdown(findings));
        
        if (findings != null && !findings.isEmpty()) {
            report.append("## Issue Details\n\n");
            
            // Limit the number of issues displayed
            List<Finding> displayFindings = findings;
            if (maxIssues > 0 && findings.size() > maxIssues) {
                report.append("> **Note**: Showing only the first ").append(maxIssues).append(" issues.\n\n");
                displayFindings = findings.stream().limit(maxIssues).collect(java.util.stream.Collectors.toList());
            }

            for (Finding finding : displayFindings) {
                report.append("### ").append(finding.getTitle()).append("\n\n");
                report.append("- **Severity**: ").append(com.codeguardian.enums.SeverityEnum.fromValue(finding.getSeverity()).name()).append("\n");
                report.append("- **Location**: ").append(finding.getLocation()).append("\n");
                if (finding.getStartLine() != null) {
                    report.append("- **Line**: ").append(finding.getStartLine());
                    if (finding.getEndLine() != null && !finding.getEndLine().equals(finding.getStartLine())) {
                        report.append("-").append(finding.getEndLine());
                    }
                    report.append("\n");
                }
                report.append("- **Category**: ").append(finding.getCategory() != null ? finding.getCategory() : "Uncategorized").append("\n");
                report.append("- **Description**: ").append(finding.getDescription()).append("\n");
                if (finding.getSuggestion() != null && !finding.getSuggestion().isEmpty()) {
                    report.append("- **Suggestion**: ").append(finding.getSuggestion()).append("\n");
                }
                if (finding.getDiff() != null && !finding.getDiff().isEmpty()) {
                    report.append("- **Suggested Fix (Diff)**:\n```\n").append(finding.getDiff()).append("\n```\n");
                }
                report.append("\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * Generate the HTML report
     */
    private String generateHTMLReport(ReviewTask task, List<Finding> findings, int maxIssues) {
        int critical = countBySeverity(findings, com.codeguardian.enums.SeverityEnum.CRITICAL.getValue());
        int high = countBySeverity(findings, com.codeguardian.enums.SeverityEnum.HIGH.getValue());
        int medium = countBySeverity(findings, com.codeguardian.enums.SeverityEnum.MEDIUM.getValue());
        int low = countBySeverity(findings, com.codeguardian.enums.SeverityEnum.LOW.getValue());

        String scopeCode = task.getScope() != null ? task.getScope() : "";
        String sampleCode = prepareSampleCode(task, scopeCode);

        StringBuilder html = new StringBuilder();
        html.append(buildHtmlHead());
        
        html.append("<div class=\"header\">\n");
        html.append("  <div class=\"title\">CodeGuardian Review Report</div>\n");
        // Use JS to navigate the top-level window so we don't navigate only inside the iframe
        html.append("  <a class=\"back\" href=\"#\" onclick=\"try{if(window.top&&window.top.history&&window.top.history.length>1){window.top.history.back();}else{window.top.location.href='/review';}}catch(e){window.location.href='/review';}return false;\"><i class=\"fas fa-arrow-left\"></i> Back to review</a>\n");
        html.append("</div>\n");

        html.append("<div class=\"grid\">\n");
        html.append(buildOverviewSection(task, critical, high, medium, low));
        html.append(buildConfigPanel());
        html.append("</div>\n");

        html.append(buildCodePanel(sampleCode, scopeCode));
        
        // Limit the number of issues displayed
        List<Finding> displayFindings = findings;
        if (maxIssues > 0 && findings != null && findings.size() > maxIssues) {
            displayFindings = findings.stream().limit(maxIssues).collect(java.util.stream.Collectors.toList());
            // A notice could be added above the table
        }
        
        html.append(buildFindingsTable(task, displayFindings));

        html.append("</div>\n</body>\n</html>\n");
        return html.toString();
    }

    private String prepareSampleCode(ReviewTask task, String scopeCode) {
        com.codeguardian.enums.ReviewTypeEnum rtEnum = com.codeguardian.enums.ReviewTypeEnum.fromValue(task.getReviewType());
        String rt = rtEnum.name();
        String sampleCode = scopeCode;
        try {
            if (rtEnum == com.codeguardian.enums.ReviewTypeEnum.FILE && scopeCode != null && !scopeCode.isEmpty()) {
                // If scopeCode looks like code content (contains newlines or is not a path), display it directly and do not try to read a file
                boolean looksLikeContent = scopeCode.contains("\n") || scopeCode.contains("\r") || !scopeCode.matches(".*\\.[a-zA-Z0-9]+$");
                if (!looksLikeContent) {
                    sampleCode = codeParserService.readFile(scopeCode);
                }
            } else if (rtEnum == com.codeguardian.enums.ReviewTypeEnum.DIRECTORY && scopeCode != null && !scopeCode.isEmpty()) {
                sampleCode = codeParserService.readDirectory(scopeCode);
            } else if (rtEnum == com.codeguardian.enums.ReviewTypeEnum.PROJECT && scopeCode != null && !scopeCode.isEmpty()) {
                sampleCode = codeParserService.readProject(scopeCode);
            }
        } catch (Exception e) {
            sampleCode = scopeCode;
        }
        return stripLeadingLineNumbers(sampleCode);
    }

    private String buildHtmlHead() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\" />\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        html.append("<title>CodeGuardian Review Report</title>\n");
        html.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" />\n");
        html.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css\" />\n");
        html.append("<style>");
        html.append(":root{--bg:#0d1117;--card:#161b22;--text:#c9d1d9;--text2:#8b949e;--border:#30363d;--primary:#58a6ff;--critical:#f44336;--high:#ff9800;--medium:#ffc107;--low:#4caf50;--editor-bg:#0d1117;--editor-line-number:#6e7681;--editor-text:#c9d1d9;--editor-padding:20px;}");
        html.append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Helvetica,Arial,sans-serif}\n");
        html.append(".wrapper{padding:24px} .header{display:flex;justify-content:space-between;align-items:center;background:var(--card);border-bottom:1px solid var(--border);padding:16px 20px;border-radius:8px} .title{font-size:18px;font-weight:600} .back{padding:8px 12px;border:1px solid var(--border);border-radius:6px;color:var(--text);background:transparent;cursor:pointer} .back:hover{background:var(--bg)}\n");
        html.append(".grid{display:grid;grid-template-columns:1fr 1.2fr;gap:12px;margin-top:12px} .panel{background:var(--card);border:1px solid var(--border);border-radius:8px;overflow:hidden;display:flex;flex-direction:column} .panel-hd{padding:14px 18px;border-bottom:1px solid var(--border);font-weight:600} .panel-bd{padding:16px 18px;flex:1} .muted{color:var(--text2)}\n");
        html.append(".overview-row{display:flex;gap:24px;flex-wrap:wrap} .overview-item{min-width:200px} .stats{display:flex;gap:12px;margin-top:12px} .stat{flex:1;background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:16px;text-align:center} .stat .num{font-size:20px;font-weight:700} .stat.critical .num{color:var(--critical)} .stat.high .num{color:var(--high)} .stat.medium .num{color:var(--medium)} .stat.low .num{color:var(--low)}\n");
        html.append(".code-block{background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:12px;overflow:auto;font-family:Monaco,Menlo,Consolas,monospace;font-size:13px;line-height:1.6} pre{margin:0;white-space:pre-wrap} \n");
        html.append(".code-editor-wrapper{flex:1;overflow:auto;position:relative;background-color:var(--editor-bg)} .code-editor-container{display:flex;position:relative;min-height:100%;align-items:flex-start} .line-numbers{padding:var(--editor-padding) 12px var(--editor-padding) var(--editor-padding);font-family:'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace;font-size:14px;line-height:1.6;color:var(--editor-line-number);background-color:var(--editor-bg);text-align:right;user-select:none;white-space:pre;border-right:1px solid var(--border);min-width:50px;box-sizing:border-box} .code-editor-pre{flex:1;margin:0;padding:0;background-color:transparent;overflow:visible;font-size:14px;line-height:1.6;font-family:'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace;box-sizing:border-box} .code-editor{display:block;width:100%;min-height:100%;padding:var(--editor-padding);font-family:'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace;font-size:14px;line-height:1.6;color:var(--editor-text);background-color:transparent;border:none;outline:none;white-space:pre;overflow-wrap:normal;overflow-x:auto;tab-size:4;margin:0;box-sizing:border-box} .code-editor:focus{outline:none} .code-editor-container pre[class*=language-]{background:transparent;margin:0;padding:0;font-size:14px;line-height:1.6;font-family:'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace;box-sizing:border-box} .code-editor-container code[class*=language-]{background:transparent;color:var(--editor-text);font-size:14px;line-height:1.6;font-family:'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace;box-sizing:border-box} .code-editor-container code[class*=language-] span,.code-editor-container code[class*=language-] .token{display:inline;font-size:inherit;line-height:inherit;font-family:inherit;vertical-align:baseline;margin:0;padding:0} .code-editor-container .token.keyword{color:#ff7b72} .code-editor-container .token.string{color:#a5d6ff} .code-editor-container .token.comment{color:#8b949e} .code-editor-container .token.function{color:#d2a8ff} .code-editor-container .token.number{color:#79c0ff}\n");
        html.append(".panel.tall .panel-bd{min-height:600px;display:flex;flex-direction:column;height:100%;padding:0 18px;overflow:hidden} .panel.tall .code-editor-wrapper{flex:1;height:100%;min-height:0;margin-top:0;align-self:stretch}\n");
        html.append(".table{margin-top:12px} .table-hd{display:grid;grid-template-columns:0.8fr 1.4fr 2.2fr;padding:10px 18px;color:var(--text2);border-bottom:1px solid var(--border)} .row{display:grid;grid-template-columns:0.8fr 1.4fr 2.2fr;gap:24px;position:relative;padding:16px 18px;border-bottom:2px solid var(--border);margin-bottom:20px;padding-bottom:20px;background:var(--card)} .row:hover{background:#21262d} .bar{position:absolute;left:0;top:0;bottom:0;width:4px} .row.critical .bar{background:var(--critical)} .row.high .bar{background:var(--high)} .row.medium .bar{background:#e3b341} .row.low .bar{background:#var(--low)} .desc{color:var(--text);font-size:13px;line-height:1.6} .desc-label{color:var(--text2);font-weight:600} .suggest-label{color:var(--text2);font-weight:600;margin-top:12px;display:inline-block} .row .loc{margin-bottom:12px} .row>div:nth-child(3){margin-bottom:12px}\n");
        html.append(".badge{padding:2px 8px;border-radius:10px;font-size:11px;margin-right:8px;display:inline-flex;align-items:center;gap:2px} .badge.critical{background:var(--critical);color:#fff} .badge.high{background:var(--high);color:#fff} .badge.medium{background:var(--medium);color:#fff} .badge.low{background:var(--low);color:#fff} .badge .finding-icon-shield{color:#d29922;font-size:14px} .badge .finding-icon-check{position:absolute;font-size:8px;color:#fff;top:50%;left:50%;transform:translate(-50%,-50%);z-index:1} .badge .fa-bug,.badge .fa-cog,.badge .fa-chart-bar{color:#fff;font-size:12px} .loc{font-family:Monaco,Menlo,Consolas,monospace;color:var(--text2)}\n");
        html.append(".diff{margin-top:8px;background:var(--bg);border:1px solid var(--border);border-radius:6px;padding:10px;font-family:Monaco,Menlo,Consolas,monospace;font-size:13px} .diff-title{color:var(--text2);font-size:12px;margin-bottom:8px} .diff-line{padding:4px 8px;margin:2px 0;border-radius:4px} .removed{background:rgba(248,81,73,.15);color:var(--critical)} .added{background:rgba(63,185,80,.15);color:var(--low)}\n");
        html.append(".footer{margin-top:16px;text-align:right;color:var(--text2);font-size:12px}\n");
        html.append("</style>\n");
        html.append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css\" />\n");
        html.append("<script defer=\"defer\" src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js\"></script>\n");
        html.append("<script defer=\"defer\" src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-java.min.js\"></script>\n");
        html.append("<script defer=\"defer\" src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-javascript.min.js\"></script>\n");
        html.append("<script defer=\"defer\" src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-typescript.min.js\"></script>\n");
        html.append("<script defer=\"defer\" src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-python.min.js\"></script>\n");
        html.append("</head>\n<body>\n<div class=\"wrapper\">\n");
        return html.toString();
    }

    private String buildOverviewSection(ReviewTask task, int critical, int high, int medium, int low) {
        StringBuilder html = new StringBuilder();
        html.append("  <div class=\"panel\" style=\"grid-column:1 / 3\">\n");
        html.append("    <div class=\"panel-hd\">Overview</div>\n");
        html.append("    <div class=\"panel-bd\">\n");
        html.append("      <div class=\"overview-row\">\n");
        String createdAtStr = task.getCreatedAt() != null ? TIME_FORMATTER.format(task.getCreatedAt()) : "";
        html.append("        <div class=\"overview-item\"><div class=\"muted\">Generated</div><div>").append(escapeHtml(createdAtStr)).append("</div></div>\n");
        html.append("        <div class=\"overview-item\"><div class=\"muted\">Scope</div><div>").append(escapeHtml(reviewTypeLabel(task.getReviewType()))).append("</div></div>\n");
        html.append("        <div class=\"overview-item\"><div class=\"muted\">Name</div><div>").append(escapeHtml(task.getName())).append("</div></div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"stats\">\n");
        html.append("        <div class=\"stat critical\"><div class=\"muted\">Critical</div><div class=\"num\">"+critical+"</div></div>\n");
        html.append("        <div class=\"stat high\"><div class=\"muted\">High</div><div class=\"num\">"+high+"</div></div>\n");
        html.append("        <div class=\"stat medium\"><div class=\"muted\">Medium</div><div class=\"num\">"+medium+"</div></div>\n");
        html.append("        <div class=\"stat low\"><div class=\"muted\">Low</div><div class=\"num\">"+low+"</div></div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        return html.toString();
    }

    private String buildConfigPanel() {
        StringBuilder html = new StringBuilder();
        html.append("  <div class=\"panel config-panel\">\n");
        html.append("    <div class=\"panel-hd\">Configuration & Scope</div>\n");
        html.append("    <div class=\"panel-bd\">\n");
        html.append("      <div class=\"muted\">Enabled rules: None</div>\n");
        html.append("      <div class=\"muted\">Collection tags: None</div>\n");
        html.append("      <div class=\"muted\">Ignored paths: None</div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        return html.toString();
    }

    private String buildCodePanel(String sampleCode, String scopeCode) {
        StringBuilder html = new StringBuilder();
        html.append("  <div class=\"panel code-panel\">\n");
        html.append("    <div class=\"panel-hd\">Code Sample</div>\n");
        html.append("    <div class=\"panel-bd\">\n");
        String normalized = normalizeLineEndings(sampleCode);
        int actualLineCount = countLines(normalized);
        StringBuilder ln = buildLineNumbers(actualLineCount);
        String lang = detectLanguage(scopeCode);
        html.append("      <div class=\"code-editor-wrapper\">\n");
        html.append("        <div class=\"code-editor-container\">\n");
        html.append("          <div class=\"line-numbers\">").append(escapeHtml(ln.toString())).append("</div>\n");
        html.append("          <pre class=\"code-editor-pre\"><code class=\"").append(lang).append(" code-editor\">").append(escapeHtml(normalized)).append("</code></pre>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        return html.toString();
    }

    private String buildFindingsTable(ReviewTask task, List<Finding> findings) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"panel table\">\n");
        html.append("  <div class=\"panel-hd\">Issue Details</div>\n");
        if (findings == null || findings.isEmpty()) {
            html.append("  <div class=\"panel-bd\"><span class=\"muted\">No issues</span></div>\n");
        } else {
            for (Finding f : findings) {
                html.append(buildFindingRow(task, f));
            }
        }
        html.append("</div>\n");
        return html.toString();
    }

    private String buildFindingRow(ReviewTask task, Finding f) {
        StringBuilder html = new StringBuilder();
        com.codeguardian.enums.SeverityEnum sEnum = com.codeguardian.enums.SeverityEnum.fromValue(f.getSeverity());
        String sev = sEnum.name().toLowerCase();
        
        // Clean the title: strip Markdown heading markers (e.g. ####:1 -> 1)
        String title = f.getTitle() != null ? f.getTitle() : "Untitled issue";

        // Debug: log the original title
        log.debug("Original title: [{}]", title);

        title = removeMdPrefix(title).trim();

        // Debug: log the cleaned title
        log.debug("Cleaned title: [{}]", title);

        String locText = resolveDisplayName(task, f);

        html.append("  <div class=\"row ").append(sev).append("\">\n");
        html.append("    <div class=\"bar\"></div>\n");

        // Column 1: location
        html.append("    <div class=\"loc\">").append(escapeHtml(locText)).append("</div>\n");

        // Column 2: severity + title
        String badgeContent = badgeIcon(f.getSeverity()) + severityLabel(f.getSeverity());
        html.append("    <div><span class=\"badge ").append(sev).append("\" style=\"position:relative;\">").append(badgeContent).append("</span> ").append(escapeHtml(title)).append("</div>\n");

        // Column 3: description
        html.append("    <div class=\"desc\">");

        // Description (prefixed with "Description:")
        if (f.getDescription() != null && !f.getDescription().isEmpty()) {
            log.debug("issueId={}, description=[{}]", f.getId(), f.getDescription());
            html.append("<strong>Description: </strong>").append(escapeHtml(f.getDescription()));
        } else {
            log.debug("issueId={}, description is empty", f.getId());
            html.append("<strong>Description: </strong>No description");
        }

        // Suggestion (if any)
        if (f.getSuggestion() != null && !f.getSuggestion().isEmpty()) {
            String suggestion = f.getSuggestion();
            html.append("<br><br><strong>Suggestion: </strong>").append(escapeHtml(suggestion));
        }

        // Suggested fix diff
        if (f.getDiff() != null && !f.getDiff().isEmpty()) {
            html.append("<br><br><br><div class=\"diff-title\">Code change example</div>");
            html.append("<div class=\"diff\">");
            String[] lines = f.getDiff().split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-")) {
                    html.append("<div class=\"diff-line removed\">").append(escapeHtml(line)).append("</div>");
                } else if (trimmed.startsWith("+")) {
                    html.append("<div class=\"diff-line added\">").append(escapeHtml(line)).append("</div>");
                } else if (!trimmed.isEmpty()) {
                    html.append("<div class=\"diff-line\">").append(escapeHtml(line)).append("</div>");
                }
            }
            html.append("</div>");
        }
        html.append("</div>\n");
        html.append("  </div>\n");
        return html.toString();
    }

    private String resolveDisplayName(ReviewTask task, Finding f) {
        String location = f.getLocation() != null ? f.getLocation() : "";
        // Normalize location, removing the Markdown heading prefix (e.g. ####:2 -> 2, or drop the prefix)
        if (!location.isEmpty()) {
            location = removeMdPrefix(location).trim();
        }

        // Build the location display text: prefer startLine/endLine, otherwise parse from location
        String displayName = "";
        String lineNumberStr = "";

        // Prefer startLine and endLine
        if (f.getStartLine() != null) {
            // Extract the file name from location
            String className = location;
            // Try to extract the file name from location (with an extension such as .java)
            if (location.contains(":")) {
                String[] parts = location.split(":", -1);
                // Find the part that contains a file extension
                for (String part : parts) {
                    if (part.contains(".java") || part.contains(".js") || part.contains(".ts") || 
                        part.contains(".py") || part.contains(".cpp") || part.contains(".c")) {
                        className = part.trim();
                        break;
                    }
                }
                // If none is found, use the first part
                if (className.equals(location) && parts.length > 0) {
                    className = parts[0].trim();
                }
            }
            
            // If location has no file name, try to get it from the task scope
            if (!className.matches(".*\\.[a-zA-Z]+.*") && task.getScope() != null) {
                String scope = task.getScope();
                // If it is a file path, extract the file name
                if (scope.contains("/") || scope.contains("\\")) {
                    int slashIdx = Math.max(scope.lastIndexOf('/'), scope.lastIndexOf('\\'));
                    if (slashIdx >= 0 && slashIdx + 1 < scope.length()) {
                        className = scope.substring(slashIdx + 1);
                    } else {
                        className = scope;
                    }
                } else if (scope.matches(".*\\.[a-zA-Z]+.*")) {
                    // If scope contains a file extension, use it
                    className = scope;
                }
            }
            
            displayName = fileNameFromPath(className);

            // Clean up any remaining Markdown heading markers again
            displayName = removeMdPrefix(displayName).trim();
            if (displayName.matches("^#+$") || displayName.isBlank()) {
                displayName = "Code Snippet";
            }
            
            // If there is still no valid file name (no file extension), try to infer it from the code sample
            if (displayName.isEmpty() || !displayName.matches(".*\\.[a-zA-Z]+.*")) {
                // If the task reviewType is FILE, scope should be a file path
                if (com.codeguardian.enums.ReviewTypeEnum.FILE.getValue().equals(task.getReviewType()) && task.getScope() != null) {
                    String scope = task.getScope();
                    displayName = fileNameFromPath(scope);
                } else {
                    // Default to "Code Snippet", or extract from location (if it contains a method name, etc.)
                    // Try to extract the class name from location (if it contains the class keyword)
                    if (location.toLowerCase().contains("class")) {
                        // Simple extraction, assuming a format like "class UserService" or "UserService class"
                        String[] words = location.split("[\\s,]+");
                        for (int i = 0; i < words.length; i++) {
                            if (words[i].equalsIgnoreCase("class") && i + 1 < words.length) {
                                displayName = words[i + 1].replaceAll("[^a-zA-Z0-9_$]", "") + ".java";
                                break;
                            }
                        }
                    }
                    // If still not found, use the default value
                    if (displayName.isEmpty() || !displayName.matches(".*\\.[a-zA-Z]+.*")) {
                        displayName = "Code Snippet";
                    }
                }
            }
            
            // Build the line-number string
            if (f.getEndLine() != null && !f.getEndLine().equals(f.getStartLine())) {
                lineNumberStr = f.getStartLine() + "-" + f.getEndLine();
            } else {
                lineNumberStr = String.valueOf(f.getStartLine());
            }
        } else {
            // Parse from location
            if (location.contains(":")) {
                String[] parts = location.split(":", 2);
                displayName = removeMdPrefix(parts[0].trim());
                lineNumberStr = parts.length > 1 ? parts[1].trim() : "";
            } else {
                displayName = location;
            }
            displayName = fileNameFromPath(displayName);
            displayName = removeMdPrefix(displayName).trim();
            if (displayName.matches("^#+$") || displayName.isBlank()) {
                displayName = "Code Snippet";
            }
            // If the display is "Code Snippet" or has no extension, and the review type is FILE, extract the file name from scope
            if ((displayName.isEmpty() || "Code Snippet".equals(displayName) || !displayName.matches(".*\\.[a-zA-Z]+.*"))
                    && com.codeguardian.enums.ReviewTypeEnum.FILE.getValue().equals(task.getReviewType()) && task.getScope() != null) {
                String scope = task.getScope();
                displayName = fileNameFromPath(scope);
                if (displayName.isEmpty() && scope != null && !scope.isEmpty()) {
                    displayName = scope;
                }
            }
        }
        
        return displayName + (lineNumberStr.isEmpty() ? "" : ":" + lineNumberStr);
    }

    private String severityLabel(Integer severity) {
        if (severity == null) return "Low";
        com.codeguardian.enums.SeverityEnum s = com.codeguardian.enums.SeverityEnum.fromValue(severity);
        return s.getDesc();
    }
    
    private String badgeIcon(Integer severity) {
        if (severity == null) return "⚠";
        com.codeguardian.enums.SeverityEnum s = com.codeguardian.enums.SeverityEnum.fromValue(severity);

        if (s == com.codeguardian.enums.SeverityEnum.CRITICAL) {
            // CRITICAL uses a simple shield icon
            return "<i class=\"fas fa-shield-alt\"></i>";
        }
        if (s == com.codeguardian.enums.SeverityEnum.HIGH) {
            return "<i class=\"fas fa-bug\"></i>";
        }
        if (s == com.codeguardian.enums.SeverityEnum.MEDIUM) {
            return "<i class=\"fas fa-cog\"></i>";
        }
        return "<i class=\"fas fa-chart-bar\"></i>";
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String generateStatistics(List<Finding> findings) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", findings != null ? findings.size() : 0);
        stats.put("critical", countBySeverity(findings, com.codeguardian.enums.SeverityEnum.CRITICAL.getValue()));
        stats.put("high", countBySeverity(findings, com.codeguardian.enums.SeverityEnum.HIGH.getValue()));
        stats.put("medium", countBySeverity(findings, com.codeguardian.enums.SeverityEnum.MEDIUM.getValue()));
        stats.put("low", countBySeverity(findings, com.codeguardian.enums.SeverityEnum.LOW.getValue()));
        return stats.toString();
    }
    
    private String generateStatisticsMarkdown(List<Finding> findings) {
        return String.format(
                "- **Critical**: %d\n- **High**: %d\n- **Medium**: %d\n- **Low**: %d\n\n",
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.CRITICAL.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.HIGH.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.MEDIUM.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.LOW.getValue())
        );
    }
    
    private String generateStatisticsHTML(List<Finding> findings) {
        return String.format(
                "<ul><li><strong>Critical</strong>: %d</li><li><strong>High</strong>: %d</li><li><strong>Medium</strong>: %d</li><li><strong>Low</strong>: %d</li></ul>\n",
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.CRITICAL.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.HIGH.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.MEDIUM.getValue()),
                countBySeverity(findings, com.codeguardian.enums.SeverityEnum.LOW.getValue())
        );
    }
    
    private int countBySeverity(List<Finding> findings, Integer severity) {
        if (findings == null) return 0;
        return (int) findings.stream()
                .filter(f -> severity.equals(f.getSeverity()))
                .count();
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String reviewTypeLabel(Integer type) {
        com.codeguardian.enums.ReviewTypeEnum e = com.codeguardian.enums.ReviewTypeEnum.fromValue(type);
        if (e == com.codeguardian.enums.ReviewTypeEnum.SNIPPET) return "Code Snippet";
        if (e == com.codeguardian.enums.ReviewTypeEnum.FILE) return "File";
        if (e == com.codeguardian.enums.ReviewTypeEnum.DIRECTORY) return "Directory";
        if (e == com.codeguardian.enums.ReviewTypeEnum.PROJECT) return "Project";
        if (e == com.codeguardian.enums.ReviewTypeEnum.GIT) return "Git Repository";
        return "";
    }

    private String normalizeLineEndings(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private int countLines(String s) {
        String[] lines = s.split("\n", -1);
        int n = lines.length;
        return n == 0 ? 1 : n;
    }

    private StringBuilder buildLineNumbers(int count) {
        StringBuilder ln = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            ln.append(i);
            if (i < count) ln.append("\n");
        }
        return ln;
    }

    private String detectLanguage(String scopePath) {
        if (scopePath == null) return "language-java";
        String lower = scopePath.toLowerCase();
        if (lower.endsWith(".js")) return "language-javascript";
        if (lower.endsWith(".ts")) return "language-typescript";
        if (lower.endsWith(".py")) return "language-python";
        if (lower.endsWith(".java")) return "language-java";
        return "language-java";
    }

    private String stripLeadingLineNumbers(String code) {
        if (code == null) return "";
        if (!code.contains(":")) return code;
        String[] lines = code.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("^\\d+:\\s.*")) {
                int idx = line.indexOf(':');
                String rest = idx >= 0 && idx + 1 < line.length() ? line.substring(idx + 1) : line;
                if (rest.startsWith(" ")) rest = rest.substring(1);
                out.append(rest);
            } else {
                out.append(line);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private String removeMdPrefix(String s) {
        if (s == null) return "";

        String original = s;

        // First try to match a Markdown heading marker at the start of the line
        Matcher m = MD_PREFIX.matcher(s);
        if (m.find()) {
            s = m.replaceFirst("");
            log.debug("MD_PREFIX matched and replaced: [{}] -> [{}]", original, s);
        }

        // Extra cleanup: if the string still starts with '#', keep removing it
        while (s.startsWith("#")) {
            s = s.substring(1).trim();
            log.debug("Removed leading #: [{}] -> [{}]", original, s);
            original = s;
        }

        // Clean up leading colons and spaces. Both the ASCII and the fullwidth colon are
        // handled, since a model may emit either regardless of the prompt language.
        while (s.startsWith(":") || s.startsWith("：")) {
            s = s.substring(1).trim();
            log.debug("Removed leading colon: [{}] -> [{}]", original, s);
            original = s;
        }

        // Final cleanup: if the whole string is only '#' characters, return an empty string
        if (s.matches("^#+$")) {
            log.debug("String is all # characters; returning empty string");
            return "";
        }

        String result = s.trim();
        log.debug("Final cleaned result: [{}]", result);
        return result;
    }

    private String fileNameFromPath(String path) {
        if (path == null || path.isBlank()) return "";
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 && idx + 1 < path.length() ? path.substring(idx + 1) : path;
    }
}
