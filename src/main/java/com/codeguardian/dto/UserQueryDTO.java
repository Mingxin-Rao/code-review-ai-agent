package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User query DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQueryDTO {
    
    /**
     * Search keyword (username, email)
     */
    private String keyword;
    
    /**
     * Status filter (0=active, 1=inactive, 2=locked)
     */
    private Integer status;
    
    /**
     * Role code filter
     */
    private String roleCode;
    
    /**
     * Page number (0-based)
     */
    @Builder.Default
    private Integer page = 0;
    
    /**
     * Page size
     */
    @Builder.Default
    private Integer size = 10;
}

