package com.codeguardian.repository;

import com.codeguardian.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for user-role associations.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    
    /**
     * Find all role associations for a user ID.
     */
    List<UserRole> findByUserId(Long userId);
    
    /**
     * Find all user associations for a role ID.
     */
    List<UserRole> findByRoleId(Long roleId);
    
    /**
     * Delete all role associations for a user.
     */
    void deleteByUserId(Long userId);
    
    /**
     * Delete all user associations for a role.
     */
    void deleteByRoleId(Long roleId);
    
    /**
     * Check whether a user holds a given role.
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
    
    /**
     * List the role codes for a user ID.
     */
    @Query("SELECT r.code FROM UserRole ur JOIN Role r ON ur.roleId = r.id WHERE ur.userId = :userId AND r.status = 0")
    List<String> findRoleCodesByUserId(Long userId);
    
    /**
     * List the role names for a user ID.
     */
    @Query("SELECT r.name FROM UserRole ur JOIN Role r ON ur.roleId = r.id WHERE ur.userId = :userId AND r.status = 0 ORDER BY r.id")
    List<String> findRoleNamesByUserId(Long userId);
}

