package com.codeguardian.service.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF generation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfGenerationResult {
    /**
     * Whether it succeeded
     */
    private boolean success;

    /**
     * PDF byte array
     */
    private byte[] pdfBytes;

    /**
     * File name
     */
    private String fileName;

    /**
     * Error message
     */
    private String errorMessage;
}
