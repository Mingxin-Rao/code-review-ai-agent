package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Git clone response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCloneResponseDTO {
    
    /**
     * Local storage path
     */
    private String localPath;
    
    /**
     * File list
     */
    private List<String> fileList;
    
    /**
     * Whether the operation succeeded
     */
    private boolean success;
    
    /**
     * Error message
     */
    private String error;
}
