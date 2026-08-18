package com.codeguardian.controller;

import com.codeguardian.dto.PermissionDTO;
import com.codeguardian.entity.Permission;
import com.codeguardian.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;

import java.util.List;

/**
 * Permission management controller
 */
@Controller
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
@Slf4j
public class PermissionController {
    
    private final PermissionService permissionService;
    
    /**
     * Permission management page
     *
     * <p>Requires login.</p>
     */
    @GetMapping
    @SaCheckLogin
    public String permissionManagementPage(Model model, jakarta.servlet.http.HttpSession session) {
        com.codeguardian.util.ViewModelUtils.populateUserInfo(model, session);
        return "admin/permissions";
    }
    
    /**
     * Query all permissions (API)
     *
     * <p>Requires `CONFIG` permission.</p>
     */
    @GetMapping("/api")
    @ResponseBody
    @SaCheckPermission("CONFIG")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(permissions);
    }
    
    /**
     * Query all permissions (DTO) (API)
     *
     * <p>Requires `CONFIG` permission.</p>
     */
    @GetMapping("/api/dto")
    @ResponseBody
    @SaCheckPermission("CONFIG")
    public ResponseEntity<List<PermissionDTO>> getAllPermissionDTOs() {
        List<PermissionDTO> permissions = permissionService.getAllPermissionDTOs();
        return ResponseEntity.ok(permissions);
    }
}
