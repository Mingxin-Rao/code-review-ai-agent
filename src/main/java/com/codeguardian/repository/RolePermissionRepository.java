package com.codeguardian.repository;

import com.codeguardian.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for role-permission associations.
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    
    /**
     * Find all permission associations for a role ID.
     */
    List<RolePermission> findByRoleId(Long roleId);
    
    /**
     * Find all role associations for a permission ID.
     */
    List<RolePermission> findByPermissionId(Long permissionId);
    
    /**
     * Delete all permission associations for a role.
     */
    void deleteByRoleId(Long roleId);
    
    /**
     * Delete all role associations for a permission.
     */
    void deleteByPermissionId(Long permissionId);
    
    /**
     * Check whether a role holds a given permission.
     */
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
    
    /**
     * List the permission codes for a role ID.
     */
    @Query("SELECT p.code FROM RolePermission rp JOIN Permission p ON rp.permissionId = p.id WHERE rp.roleId = :roleId")
    List<String> findPermissionCodesByRoleId(Long roleId);
}

