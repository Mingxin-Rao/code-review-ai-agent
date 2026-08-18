package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Code review response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDTO {
    
    /**
     * Task ID
     */
    private Long taskId;
    
    /**
     * Task name
     */
    private String taskName;
    
    /**
     * Task status
     */
    private String status;

    /**
     * Review type
     */
    private String reviewType;
    
    /**
     * Review scope
     */
    private String scope;
    
    /**
     * Created time
     */
    private LocalDateTime createdAt;
    
    /**
     * Total number of issues
     */
    private Integer totalFindings;
    
    /**
     * Critical issue count
     */
    private Integer criticalCount;
    
    /**
     * High priority issue count
     */
    private Integer highCount;
    
    /**
     * Medium priority issue count
     */
    private Integer mediumCount;
    
    /**
     * Low priority issue count
     */
    private Integer lowCount;
}

