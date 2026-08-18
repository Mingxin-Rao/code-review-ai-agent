package com.codeguardian.service;

import com.codeguardian.dto.*;
import com.codeguardian.entity.Permission;
import com.codeguardian.entity.Role;
import com.codeguardian.entity.RolePermission;
import com.codeguardian.repository.PermissionRepository;
import com.codeguardian.repository.RolePermissionRepository;
import com.codeguardian.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Role service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    
    /**
     * Query all roles
     */
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
    
    /**
     * Query all roles (including permission info)
     */
    @Transactional(readOnly = true)
    public List<RoleDTO> getAllRolesWithPermissions() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(role -> {
            List<String> permissions = rolePermissionRepository.findPermissionCodesByRoleId(role.getId());
            return RoleDTO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .permissions(permissions)
                .build();
        }).collect(Collectors.toList());
    }
    
    /**
     * Query a role by code
     */
    @Transactional(readOnly = true)
    public Role getRoleByCode(String code) {
        return roleRepository.findByCode(code)
            .orElseThrow(() -> new RuntimeException("Role not found"));
    }
    
    /**
     * Query a role by ID
     */
    @Transactional(readOnly = true)
    public RoleDTO getRoleById(Long id) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Role not found"));
        List<String> permissions = rolePermissionRepository.findPermissionCodesByRoleId(role.getId());
        return RoleDTO.builder()
            .id(role.getId())
            .code(role.getCode())
            .name(role.getName())
            .description(role.getDescription())
            .status(role.getStatus())
            .permissions(permissions)
            .build();
    }
    
    /**
     * Create a role
     */
    @Transactional
    public RoleDTO createRole(RoleCreateDTO createDTO) {
        // check whether the role code already exists
        if (roleRepository.existsByCode(createDTO.getCode())) {
            throw new RuntimeException("Role code already exists");
        }
        
        // create the role
        Role role = Role.builder()
            .code(createDTO.getCode())
            .name(createDTO.getName())
            .description(createDTO.getDescription())
            .status(createDTO.getStatus() != null ? createDTO.getStatus() : 0)
            .createdAt(LocalDateTime.now())
            .build();
        
        role = roleRepository.save(role);
        
        // assign permissions
        if (createDTO.getPermissionCodes() != null && !createDTO.getPermissionCodes().isEmpty()) {
            assignPermissions(role.getId(), createDTO.getPermissionCodes());
        }
        
        log.info("Role created successfully: code={}, id={}", role.getCode(), role.getId());
        return getRoleById(role.getId());
    }
    
    /**
     * Update a role
     */
    @Transactional
    public RoleDTO updateRole(Long id, RoleUpdateDTO updateDTO) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Role not found"));
        
        // update basic information
        if (StringUtils.hasText(updateDTO.getName())) {
            role.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            role.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getStatus() != null) {
            role.setStatus(updateDTO.getStatus());
        }
        
        role = roleRepository.save(role);
        
        // update permissions
        if (updateDTO.getPermissionCodes() != null) {
            assignPermissions(id, updateDTO.getPermissionCodes());
        }
        
        log.info("Role updated successfully: id={}", id);
        return getRoleById(id);
    }
    
    /**
     * Delete a role
     */
    @Transactional
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new RuntimeException("Role not found");
        }
        
        // delete role-permission associations
        rolePermissionRepository.deleteByRoleId(id);
        
        // delete the role
        roleRepository.deleteById(id);
        
        log.info("Role deleted successfully: id={}", id);
    }
    
    /**
     * Assign permissions
     */
    @Transactional
    public void assignPermissions(Long roleId, List<String> permissionCodes) {
        // delete existing permissions
        rolePermissionRepository.deleteByRoleId(roleId);
        
        // add new permissions
        for (String permissionCode : permissionCodes) {
            Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new RuntimeException("Permission not found: " + permissionCode));
            
            RolePermission rolePermission = RolePermission.builder()
                .roleId(roleId)
                .permissionId(permission.getId())
                .createdAt(LocalDateTime.now())
                .build();
            
            rolePermissionRepository.save(rolePermission);
        }
        
        log.info("Permissions assigned successfully: roleId={}, permissions={}", roleId, permissionCodes);
    }
}

