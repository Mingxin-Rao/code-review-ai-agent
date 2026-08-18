package com.codeguardian.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Update-role DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateDTO {
    
    @Size(min = 2, max = 64, message = "Role name length must be between 2 and 64")
    private String name;
    
    private String description;
    
    private Integer status;
    
    private List<String> permissionCodes; // Permission code list
}

