package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File content DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContentDTO {
    
    /**
     * File path (relative path)
     */
    private String path;
    
    /**
     * File content
     */
    private String content;
}
