package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paged result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    
    /**
     * Data list
     */
    private List<T> content;
    
    /**
     * Total number of records
     */
    private Long totalElements;
    
    /**
     * Total number of pages
     */
    private Integer totalPages;
    
    /**
     * Current page number (0-based)
     */
    private Integer page;
    
    /**
     * Page size
     */
    private Integer size;
    
    /**
     * Whether there is a previous page
     */
    private Boolean hasPrevious;
    
    /**
     * Whether there is a next page
     */
    private Boolean hasNext;
}

