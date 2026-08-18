package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    
    /**
     * Whether the operation succeeded
     */
    private Boolean success;
    
    /**
     * Message
     */
    private String message;
    
    /**
     * User ID
     */
    private Long userId;
    
    /**
     * Username
     */
    private String username;
    
    /**
     * Real name
     */
    private String realName;
    
    /**
     * Token (can be extended to JWT later)
     */
    private String token;
}

