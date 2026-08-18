package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Code-review report.
 */
@Entity
@Table(name = "review_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * ID of the associated review task
     */
    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;
    
    /**
     * Report body (HTML)
     */
    @Column(columnDefinition = "TEXT")
    private String htmlContent;
    
    /**
     * Report body (Markdown)
     */
    @Column(columnDefinition = "TEXT")
    private String markdownContent;
    
    /**
     * Summary statistics (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String statistics;
    
    /**
     * Creation timestamp
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

