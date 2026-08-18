package com.codeguardian.entity;

import jakarta.persistence.*;
import com.codeguardian.enums.TaskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Code-review task entity.
 */
@Entity
@Table(name = "review_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Task name
     */
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, columnDefinition = "integer")
    private Integer reviewType;
    
    /**
     * Review scope (a path or a code snippet)
     */
    @Column(columnDefinition = "TEXT")
    private String scope;
    
    @Column(nullable = false, columnDefinition = "integer")
    private Integer status;
    
    /**
     * Creation timestamp
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Completion timestamp
     */
    private LocalDateTime completedAt;
    
    
    /**
     * Error message (when the task failed)
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TaskStatusEnum.PENDING.getValue();
        }
    }
}
