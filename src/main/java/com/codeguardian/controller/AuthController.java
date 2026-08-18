package com.codeguardian.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codeguardian.dto.LoginRequestDTO;
import com.codeguardian.dto.LoginResponseDTO;
import com.codeguardian.dto.UserCreateDTO;
import com.codeguardian.dto.UserDTO;
import com.codeguardian.service.AuthService;
import com.codeguardian.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthService authService;
    private final UserService userService;

    private void performLogout(HttpServletRequest request, HttpServletResponse response) {
        StpUtil.logout();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        String tokenName = cn.dev33.satoken.SaManager.getConfig().getTokenName();
        Cookie cookie = new Cookie(tokenName, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }
    
    /**
     * Show the login page
     *
     * @param model view model; injects the login form data
     * @return login page template name
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("loginRequest", new LoginRequestDTO());
        return "login";
    }
    
    /**
     * Handle login form submission
     *
     * <p>Validates the parameters then calls the auth service; on success saves user info to the session, on failure returns an error message.</p>
     *
     * @param request login request data (username or email + password)
     * @param bindingResult parameter validation result
     * @param model view model, used to return error messages
     * @param httpRequest raw HTTP request, used to resolve the client IP
     * @return on success redirects to the user management page, on failure returns the login page
     */
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequest") LoginRequestDTO request,
                       BindingResult bindingResult,
                       Model model,
                       HttpServletRequest httpRequest) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", "Please fill in all login fields");
            return "login";
        }
        
        // get the client IP
        String clientIp = getClientIp(httpRequest);
        
        // perform login
        LoginResponseDTO response = authService.login(request, clientIp);
        
        if (response.getSuccess()) {
            // login succeeded, save user info to the session
            HttpSession session = httpRequest.getSession(true); // ensure a new session is created
            session.setAttribute("userId", response.getUserId());
            session.setAttribute("username", response.getUsername());
            session.setAttribute("roleName", response.getRealName()); // realName now stores the role name
            session.setAttribute("realName", response.getRealName()); // kept for compatibility
            
            // set the session timeout (30 minutes)
            session.setMaxInactiveInterval(30 * 60);
            
            log.info("Saved user info to session: userId={}, username={}, roleName={}, sessionId={}",
                response.getUserId(), response.getUsername(), response.getRealName(), session.getId());
            
            // login succeeded, redirect to the dashboard
            return "redirect:/dashboard";
        } else {
            // login failed, return the error message
            model.addAttribute("error", response.getMessage());
            return "login";
        }
    }
    
    /**
     * Login API (AJAX)
     *
     * <p>On success returns a response object containing `token`; on failure returns 400.</p>
     *
     * @param request login request data
     * @param httpRequest raw HTTP request
     * @return login response object, including success flag and message
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<LoginResponseDTO> loginApi(@Valid @RequestBody LoginRequestDTO request,
                                                      HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        LoginResponseDTO response = authService.login(request, clientIp);
        
        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Registration API
     *
     * <p>Creates a new user and automatically assigns the `VIEWER` role; after success, auto-logs in and returns a token.</p>
     *
     * @param createDTO user creation request
     * @param httpRequest raw HTTP request
     * @return login response object (auto-login right after registration)
     */
    @PostMapping("/api/auth/register")
    @ResponseBody
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody UserCreateDTO createDTO,
                                                     HttpServletRequest httpRequest) {
        if (createDTO.getRoleCodes() == null || createDTO.getRoleCodes().isEmpty()) {
            createDTO.setRoleCodes(java.util.List.of("VIEWER"));
        }
        UserDTO user = userService.createUser(createDTO);
        LoginRequestDTO loginReq = LoginRequestDTO.builder()
                .usernameOrEmail(user.getUsername())
                .password(createDTO.getPassword())
                .build();
        String clientIp = getClientIp(httpRequest);
        LoginResponseDTO resp = authService.login(loginReq, clientIp);
        return ResponseEntity.ok(resp);
    }

    /**
     * Logout API
     *
     * <p>Calls Sa-Token to log out the current session and clears the HTTP session.</p>
     *
     * @param request raw HTTP request
     * @return 200 indicates logout succeeded
     */
    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<Void> logoutApi(HttpServletRequest request, HttpServletResponse response) {
        performLogout(request, response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        performLogout(request, response);
        return "redirect:/login";
    }
    
    /**
     * Resolve the client IP address
     *
     * <p>Prefers reverse-proxy headers (X-Forwarded-For / X-Real-IP); falls back to `request.getRemoteAddr()` when unavailable.</p>
     *
     * @param request raw HTTP request
     * @return client IP (may be the first address in the proxy chain)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // handle multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
