package com.codeguardian.util;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

/**
 * View-model utilities.
 */
public final class ViewModelUtils {

    private ViewModelUtils() {}

    /**
     * Populate the view model with the current user's info from the session.
     *
     * @param model the view model
     * @param session the current session
     */
    public static void populateUserInfo(Model model, HttpSession session) {
        String username = (String) session.getAttribute("username");
        String roleName = (String) session.getAttribute("roleName");

        if (username == null || username.isEmpty()) {
            username = "ADMIN";
        }
        if (roleName == null || roleName.isEmpty()) {
            roleName = "System Administrator";
        }

        String avatarChar = username != null && !username.isEmpty()
                ? username.substring(0, 1).toUpperCase()
                : "A";

        model.addAttribute("username", username);
        model.addAttribute("roleName", roleName);
        model.addAttribute("avatarChar", avatarChar);

        boolean isAdmin = false;
        boolean canConfig = false;
        boolean canReview = false;
        boolean canQuery = false;
        try {
            isAdmin = StpUtil.hasPermission("ADMIN") || StpUtil.hasRole("ADMIN");
            canConfig = StpUtil.hasPermission("CONFIG") || isAdmin;
            canReview = StpUtil.hasPermission("REVIEW") || isAdmin;
            canQuery = StpUtil.hasPermission("QUERY") || isAdmin;
        } catch (Exception e) {
            // Fall back to matching the role label when the Sa-Token session is unavailable.
            isAdmin = "ADMIN".equalsIgnoreCase(roleName)
                    || "System Administrator".equalsIgnoreCase(roleName);
            canConfig = isAdmin;
            canReview = isAdmin;
            canQuery = true;
        }

        model.addAttribute("canAdmin", isAdmin);
        model.addAttribute("canConfig", canConfig);
        model.addAttribute("canReview", canReview);
        model.addAttribute("canQuery", canQuery);
    }
}
