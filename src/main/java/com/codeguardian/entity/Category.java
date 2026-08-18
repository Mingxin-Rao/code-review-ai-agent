package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Issue category entity.
 */
@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Category code (e.g. SECURITY, BUG)
     */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /**
     * Category display name (e.g. Security, Bug)
     */
    @Column(nullable = false, length = 64)
    private String name;

    /**
     * Category description
     */
    @Column(columnDefinition = "TEXT")
    private String description;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
