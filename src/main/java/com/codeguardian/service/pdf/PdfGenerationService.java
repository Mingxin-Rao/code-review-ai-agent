package com.codeguardian.service.pdf;

import com.codeguardian.entity.ReviewReport;
import com.codeguardian.exception.PdfGenerationException;
import com.codeguardian.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PDF generation service
 * Converts the HTML report into PDF format
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationService {

    private final ReportService reportService;
    private final PdfHtmlConverter htmlConverter;

    /**
     * Generate the PDF report
     * @param taskId the task ID
     * @return the PDF byte array
     * @throws PdfGenerationException thrown when PDF generation fails
     */
    public PdfGenerationResult generatePdf(Long taskId) {
        log.debug("Starting PDF report generation: taskId={}", taskId);

        ReviewReport report = reportService.generateReportForceRefresh(taskId);
        validateHtmlContent(report);

        String html = report.getHtmlContent();
        PdfGenerationResult result = new PdfGenerationResult();

        try {
            // Preprocess the HTML
            String preparedHtml = htmlConverter.prepareHtmlForPdf(html);

            // Load the font
            PdfHtmlConverter.FontLoadResult fontLoadResult = htmlConverter.loadFont();

            // Generate the PDF
            byte[] pdfBytes = convertToPdf(preparedHtml, fontLoadResult);
            result.setSuccess(true);
            result.setPdfBytes(pdfBytes);
            result.setFileName(htmlConverter.generatePdfFileName(taskId));

            log.info("PDF report generated successfully: taskId={}, size={} bytes", taskId, pdfBytes.length);
        } catch (Exception e) {
            log.error("PDF report generation failed: taskId={}", taskId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Validate the HTML content
     */
    private void validateHtmlContent(ReviewReport report) {
        if (report.getHtmlContent() == null || report.getHtmlContent().isEmpty()) {
            throw new PdfGenerationException("HTML report content is empty");
        }
    }

    /**
     * Convert the HTML into PDF
     */
    private byte[] convertToPdf(String html, PdfHtmlConverter.FontLoadResult fontLoadResult) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder = createBuilder(html, out, fontLoadResult);
        builder.run();

        return out.toByteArray();
    }

    /**
     * Create the PDF renderer
     */
    private com.openhtmltopdf.pdfboxout.PdfRendererBuilder createBuilder(
            String html,
            ByteArrayOutputStream out,
            PdfHtmlConverter.FontLoadResult fontLoadResult) throws Exception {

        com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);

        // Try to load the font
        if (fontLoadResult.isSuccess() && fontLoadResult.getFontFile() != null) {
            try {
                // Register the font with the PDF renderer
                builder.useFont(fontLoadResult.getFontFile(), "ArialUnicode");
                log.info("PDF font registered successfully: ArialUnicode, file: {}",
                        fontLoadResult.getFontFile().getAbsolutePath());
            } catch (Exception e) {
                log.error("PDF font registration failed: {}", e.getMessage(), e);
                throw e;
            }
        } else {
            // Expected when no optional CJK font is installed; the renderer uses its built-in fonts.
            log.debug("No PDF font registered ({}); CJK glyphs may not render", fontLoadResult.getErrorMessage());
        }

        return builder;
    }

    /**
     * Build the PDF response headers
     */
    public HttpHeaders buildPdfHeaders(String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName)
                .build());
        return headers;
    }
}
