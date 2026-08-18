package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Role entity.
 */
@Entity
@Table(name = "roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Role code (unique)
     */
    @Column(nullable = false, unique = true, length = 32)
    private String code;
    
    /**
     * Role name
     */
    @Column(nullable = false, length = 64)
    private String name;
    
    /**
     * Role description
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * Role status: 0=ACTIVE, 1=INACTIVE
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 0;
    
    /**
     * Creation timestamp
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Last-update timestamp
     */
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = 0; // ACTIVE
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

