package com.codeguardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Create-role DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreateDTO {
    
    @NotBlank(message = "Role code must not be empty")
    @Size(min = 2, max = 32, message = "Role code length must be between 2 and 32")
    private String code;
    
    @NotBlank(message = "Role name must not be empty")
    @Size(min = 2, max = 64, message = "Role name length must be between 2 and 64")
    private String name;
    
    private String description;
    
    @Builder.Default
    private Integer status = 0; // Active by default

    private List<String> permissionCodes; // Permission code list
}

