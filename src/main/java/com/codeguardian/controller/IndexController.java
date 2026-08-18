package com.codeguardian.controller;

import com.codeguardian.dto.ApiIndexDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Root path controller
 */
@Controller
public class IndexController {
    
    /**
     * Handles root-path access (redirects to the dashboard; unauthenticated requests are handled by the interceptor)
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }
    
    /**
     * API root path (returns JSON)
     */
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<ApiIndexDTO> apiIndex() {
        ApiIndexDTO dto = ApiIndexDTO.builder()
                .name("CodeGuardian AI")
                .version("1.0.0")
                .description("Professional Code Review AI Agent")
                .endpoints(Map.of(
                        "health", "/actuator/health",
                        "api", "/api/review",
                        "login", "/api/auth/login"
                ))
                .build();
        return ResponseEntity.ok(dto);
    }
}
