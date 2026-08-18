package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    private Long id;
    private String username;
    private String email;
    private String realName;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private List<String> roles; // Role code list
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}

