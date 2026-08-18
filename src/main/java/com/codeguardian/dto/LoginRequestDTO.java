package com.codeguardian.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {
    
    /**
     * Username or email
     */
    @NotBlank(message = "Username or email must not be empty")
    private String usernameOrEmail;
    
    /**
     * Password
     */
    @NotBlank(message = "Password must not be empty")
    private String password;
}

