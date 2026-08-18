package com.codeguardian.controller;

import com.codeguardian.entity.ReviewReport;
import com.codeguardian.service.ReportService;
import com.codeguardian.service.pdf.PdfGenerationService;
import com.codeguardian.service.pdf.PdfGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Report controller
 * <p><p>Provides the HTTP endpoints for report generation and export</p>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final PdfGenerationService pdfGenerationService;

    /**
     * Generate the review report
     *
     * @param taskId the task ID
     * @return the report generation result
     */
    @PostMapping("/{taskId}")
    public ResponseEntity<ApiResponse> generateReport(@PathVariable("taskId") Long taskId) {
        try {
            ReviewReport report = reportService.generateReport(taskId);
            String response = String.format("{\"message\":\"Report generated successfully\",\"reportId\":%d}", report.getId());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiResponse(true, response, null));
        } catch (Exception e) {
            log.error("Failed to generate report: taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, null, e.getMessage()));
        }
    }

    /**
     * Get the report in HTML format
     *
     * @param taskId the task ID
     * @return the HTML report content
     */
    @GetMapping("/{taskId}/html")
    public ResponseEntity<String> getHtmlReport(@PathVariable("taskId") Long taskId) {
        try {
            ReviewReport report = reportService.generateReport(taskId);

            if (report.getHtmlContent() == null || report.getHtmlContent().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(report.getHtmlContent());
        } catch (Exception e) {
            log.error("Failed to get HTML report: taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get the report in Markdown format
     *
     * @param taskId the task ID
     * @return the Markdown report content
     */
    @GetMapping("/{taskId}/markdown")
    public ResponseEntity<String> getMarkdownReport(@PathVariable("taskId") Long taskId) {
        try {
            ReviewReport report = reportService.generateReport(taskId);

            if (report.getMarkdownContent() == null || report.getMarkdownContent().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(report.getMarkdownContent());
        } catch (Exception e) {
            log.error("Failed to get Markdown report: taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get the report in PDF format
     *
     * @param taskId the task ID
     * @return the PDF report file
     */
    @GetMapping("/{taskId}/pdf")
    public ResponseEntity<byte[]> getPdfReport(@PathVariable("taskId") Long taskId) {
        try {
            PdfGenerationResult result = pdfGenerationService.generatePdf(taskId);

            if (!result.isSuccess()) {
                log.error("PDF generation failed: taskId={}, error={}", taskId, result.getErrorMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            HttpHeaders headers = pdfGenerationService.buildPdfHeaders(result.getFileName());
            return new ResponseEntity<>(result.getPdfBytes(), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Failed to get PDF report: taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Unified API response format
     */
    public static class ApiResponse {
        private final boolean success;
        private final String data;
        private final String error;

        public ApiResponse(boolean success, String data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getData() {
            return data;
        }

        public String getError() {
            return error;
        }
    }
}
