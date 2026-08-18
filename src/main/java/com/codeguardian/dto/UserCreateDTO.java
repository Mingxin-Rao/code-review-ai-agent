package com.codeguardian.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Create-user DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateDTO {
    
    @NotBlank(message = "Username must not be empty")
    @Size(min = 3, max = 32, message = "Username length must be between 3 and 32")
    private String username;
    
    @NotBlank(message = "Email must not be empty")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password must not be empty")
    @Size(min = 6, max = 32, message = "Password length must be between 6 and 32")
    private String password;
    
    private String realName;
    private String phone;
    @Builder.Default
    private Integer status = 0; // Active by default
    private List<String> roleCodes; // Role code list
}

