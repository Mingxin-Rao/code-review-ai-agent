package com.codeguardian.controller;

import com.codeguardian.model.dto.SettingsDTO;
import com.codeguardian.dto.OperationResponseDTO;
import com.codeguardian.service.SystemConfigService;
import com.codeguardian.util.ViewModelUtils;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * System Settings controller
 */
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final SystemConfigService configService;

    /**
     * Settings page
     * <p>Requires `CONFIG` permission.</p>
     */
    @GetMapping
    @SaCheckPermission("CONFIG")
    public String settingsPage(Model model, HttpSession session) {
        ViewModelUtils.populateUserInfo(model, session);
        model.addAttribute("settings", configService.getSettings());
        return "admin/settings";
    }

    /**
     * Save settings
     */
    @PostMapping("/save")
    @ResponseBody
    @SaCheckPermission("CONFIG")
    public ResponseEntity<OperationResponseDTO> saveSettings(@RequestBody SettingsDTO settings) {
        try {
            configService.saveSettings(settings);
            return ResponseEntity.ok(OperationResponseDTO.success("Settings saved"));
        } catch (Exception e) {
            log.error("Failed to save settings", e);
            return ResponseEntity.badRequest().body(OperationResponseDTO.error(e.getMessage()));
        }
    }

    /**
     * Export settings
     */
    @GetMapping("/export")
    @ResponseBody
    @SaCheckPermission("CONFIG")
    public ResponseEntity<SettingsDTO> exportSettings() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=settings.json");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(configService.getSettings());
    }

    /**
     * Import settings
     */
    @PostMapping("/import")
    @ResponseBody
    @SaCheckPermission("CONFIG")
    public ResponseEntity<OperationResponseDTO> importSettings(@RequestBody SettingsDTO settings) {
        try {
            configService.saveSettings(settings);
            return ResponseEntity.ok(OperationResponseDTO.success("Settings imported successfully"));
        } catch (Exception e) {
            log.error("Import settings failed", e);
            return ResponseEntity.badRequest().body(OperationResponseDTO.error(e.getMessage()));
        }
    }
}
