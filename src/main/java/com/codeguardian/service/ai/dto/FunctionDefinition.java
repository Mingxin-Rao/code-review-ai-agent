package com.codeguardian.service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Function definition DTO
 *
 * <p>Defines the details of a tool function, including its name, description, and parameter schema</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDefinition {
    /**
     * Function name
     */
    private String name;

    /**
     * Function description
     */
    private String description;

    /**
     * Function parameter schema (in JSON Schema format)
     */
    private Map<String, Object> parameters;
}
