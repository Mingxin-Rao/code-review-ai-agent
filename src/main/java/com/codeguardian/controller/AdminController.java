package com.codeguardian.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin console controller
 */
@Controller
@RequestMapping("/admin")
public class AdminController {
    
    /**
     * Admin console home; redirects to User Management
     */
    @GetMapping
    public String adminIndex() {
        return "redirect:/admin/users";
    }
}

