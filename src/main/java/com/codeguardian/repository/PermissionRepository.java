package com.codeguardian.repository;

import com.codeguardian.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for permissions.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    
    /**
     * Find a permission by its code.
     */
    Optional<Permission> findByCode(String code);
    
    /**
     * Check whether a permission code exists.
     */
    boolean existsByCode(String code);
}

