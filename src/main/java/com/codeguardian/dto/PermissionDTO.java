package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Permission DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String resource; // Resource info, e.g. "TASK, REPORT" or "ALL"
    private String action;   // Action info, e.g. "READ" or "CREATE, READ" or "ALL"
}

