package com.codeguardian.service.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rule definition model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {
    /** Rule ID */
    private Integer id;

    /** Rule name */
    private String name;

    /** Rule description / key point */
    private String description;

    /** Regex match pattern */
    private String pattern;

    /** Severity: CRITICAL, HIGH, MEDIUM, LOW */
    private String severity;

    /** Weight (0-100) */
    private int weight;

    /** Fix suggestion */
    private String suggestion;
}
