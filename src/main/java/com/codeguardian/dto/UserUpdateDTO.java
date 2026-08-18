package com.codeguardian.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Update-user DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDTO {
    
    @Size(max = 64, message = "Real name must not exceed 64 characters")
    private String realName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @Size(max = 16, message = "Phone number must not exceed 16 characters")
    private String phone;
    
    private Integer status;
    private List<String> roleCodes; // Role code list
}

