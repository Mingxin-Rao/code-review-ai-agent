package com.codeguardian.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.codeguardian.service.ai.factory.ChatClientFactory;

/**
 * Code-review page controller.
 */
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
@Slf4j
public class ReviewPageController {

    private final com.codeguardian.service.SystemConfigService configService;
    private final ChatClientFactory chatClientFactory;
    
    /**
     * Code-review page.
     */
    @GetMapping
    @SaCheckLogin
    public String reviewPage(Model model, jakarta.servlet.http.HttpSession session) {
        com.codeguardian.util.ViewModelUtils.populateUserInfo(model, session);
        
        // Read the configured project root and scope settings
        com.codeguardian.model.dto.SettingsDTO settings = configService.getSettings();
        model.addAttribute("projectRoot", settings.getProjectRoot());
        model.addAttribute("includePaths", settings.getIncludePaths());
        model.addAttribute("excludePaths", settings.getExcludePaths());
        model.addAttribute("maxIssues", settings.getMaxIssues());
        model.addAttribute("ruleStandard", settings.getRuleStandard());
        model.addAttribute("availableModelProviders", chatClientFactory.getAvailableProviders());
        model.addAttribute("hasAvailableModelProviders", chatClientFactory.hasAvailableProviders());
        model.addAttribute("defaultModelProvider", chatClientFactory.getDefaultProvider());
        
        return "review";
    }

    /**
     * Report history page.
     */
    @GetMapping("/reports")
    @SaCheckLogin
    public String historyPage(Model model, jakarta.servlet.http.HttpSession session) {
        com.codeguardian.util.ViewModelUtils.populateUserInfo(model, session);
        return "history";
    }
}
