package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An issue found during code review.
 */
@Entity
@Table(name = "findings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * ID of the associated review task
     */
    @Column(name = "task_id", nullable = false)
    private Long taskId;
    
    @Column(nullable = false, columnDefinition = "integer")
    private Integer severity;
    
    /**
     * Issue title
     */
    @Column(nullable = false)
    private String title;
    
    /**
     * Issue location (file path or position in the code)
     */
    @Column(nullable = false)
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
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
    
    /**
     * Suggested fix
     */
    @Column(columnDefinition = "TEXT")
    private String suggestion;
    
    @Column(columnDefinition = "TEXT")
    private String diff;
    
    /**
     * Issue category code
     */
    @Column(name = "category", length = 32)
    private String category;
    
    /**
     * Issue source: AI, Semgrep, RuleEngine
     */
    @Column(name = "source")
    private String source;
}
