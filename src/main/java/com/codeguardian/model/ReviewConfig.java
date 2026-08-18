package com.codeguardian.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Review config
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewConfig {
    
    /**
     * Whether to check security issues
     */
    @Builder.Default
    private boolean checkSecurity = true;
    
    /**
     * Whether to check performance issues
     */
    @Builder.Default
    private boolean checkPerformance = true;
    
    /**
     * Whether to check logic errors
     */
    @Builder.Default
    private boolean checkLogic = true;
    
    /**
     * Whether to check code style
     */
    @Builder.Default
    private boolean checkStyle = false;
    
    /**
     * Whether to check maintainability
     */
    @Builder.Default
    private boolean checkMaintainability = true;
    
    /**
     * Review strategy: SECURITY_FIRST, BALANCED, PERFORMANCE_FIRST
     */
    @Builder.Default
    private ReviewStrategy strategy = ReviewStrategy.BALANCED;
    
    /**
     * List of ignored path patterns
     */
    private List<String> ignorePaths;
    
    /**
     * Whether to enable AI analysis
     */
    @Builder.Default
    private boolean enableAI = true;
    
    /**
     * AI model config
     */
    private AIConfig aiConfig;
    
    public enum ReviewStrategy {
        SECURITY_FIRST,    // Security first
        BALANCED,          // Balanced mode
        PERFORMANCE_FIRST  // Performance first
    }
}


