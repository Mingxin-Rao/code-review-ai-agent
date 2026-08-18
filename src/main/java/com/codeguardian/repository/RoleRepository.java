package com.codeguardian.repository;

import com.codeguardian.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for roles.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    /**
     * Find a role by its code.
     */
    Optional<Role> findByCode(String code);
    
    /**
     * Check whether a role code exists.
     */
    boolean existsByCode(String code);
}

