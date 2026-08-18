package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Custom rule DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRuleDTO {
    /** Name */
    private String name;
    /** Key point / description */
    private String point;
    /** Regex pattern (matches violations) */
    private String pattern;
    /** Language: JAVA/JS/TS/PY */
    private String language;
    /** Severity: CRITICAL/HIGH/MEDIUM/LOW */
    private String severity;
    /** Weight (0-100) used for scoring and sorting */
    private Integer weight;
}
