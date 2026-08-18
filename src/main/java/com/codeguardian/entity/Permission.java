package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Permission entity.
 */
@Entity
@Table(name = "permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Permission code (unique)
     */
    @Column(nullable = false, unique = true, length = 32)
    private String code;
    
    /**
     * Permission name
     */
    @Column(nullable = false, length = 64)
    private String name;
    
    /**
     * Permission description
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * Resource type
     */
    @Column
    private Integer resource;
    
    /**
     * Action type
     */
    @Column
    private Integer action;
    
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

