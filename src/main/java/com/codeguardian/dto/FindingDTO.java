package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Finding DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingDTO {
    
    private Long id;
    
    /**
     * Severity
     */
    private String severity;
    
    /**
     * Issue title
     */
    private String title;
    
    /**
     * Issue location
     */
    private String location;
    
    /**
     * Start line number
     */
    private Integer startLine;
    
    /**
     * End line number
     */
    private Integer endLine;
    
    /**
     * Issue description
     */
    private String description;
    
    /**
     * Fix suggestion
     */
    private String suggestion;
    
    /**
     * Fix code diff
     */
    private String diff;
    
    /**
     * Issue category
     */
    private String category;
}

