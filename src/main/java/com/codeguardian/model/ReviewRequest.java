package com.codeguardian.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Code review request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {
    
    /**
     * Review type: PROJECT, DIRECTORY, FILE, SNIPPET
     */
    private ReviewType type;
    
    /**
     * Project path (used when type is PROJECT or DIRECTORY)
     */
    private String projectPath;
    
    /**
     * File path (used when type is FILE)
     */
    private String filePath;
    
    /**
     * Code snippet (used when type is SNIPPET)
     */
    private String codeSnippet;
    
    /**
     * Code language (used when type is SNIPPET)
     */
    private String language;
    
    /**
     * Review config
     */
    private ReviewConfig config;
    
    public enum ReviewType {
        PROJECT,    // Whole project
        DIRECTORY,  // Directory
        FILE,       // Single file
        SNIPPET     // Code snippet
    }
}


