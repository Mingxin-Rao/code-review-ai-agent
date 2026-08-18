package com.codeguardian.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * User entity.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Username
     */
    @Column(nullable = false, unique = true, length = 32)
    private String username;
    
    /**
     * Email address
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    /**
     * Password hash (BCrypt)
     */
    @Column(nullable = false, length = 60)
    private String passwordHash;
    
    /**
     * Real name
     */
    @Column(length = 64)
    private String realName;
    
    /**
     * Phone number
     */
    @Column(length = 16)
    private String phone;
    
    /**
     * Avatar URL
     */
    @Column(columnDefinition = "TEXT")
    private String avatarUrl;
    
    /**
     * User status: 0=ACTIVE, 1=INACTIVE, 2=LOCKED
     */
    @Column(nullable = false)
    private Integer status;
    
    /**
     * Last login timestamp
     */
    private LocalDateTime lastLoginAt;
    
    /**
     * Last login IP (IPv4/IPv6 string)
     */
    @Column(length = 45)
    private String lastLoginIp;
    
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
