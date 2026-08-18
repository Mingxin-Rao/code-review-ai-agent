package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git file content response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitFileResponseDTO {
    
    /**
     * File content
     */
    private String content;
    
    /**
     * Whether the operation succeeded
     */
    private boolean success;
    
    /**
     * Error message
     */
    private String error;
}
