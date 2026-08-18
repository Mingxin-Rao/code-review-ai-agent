package com.codeguardian.service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool definition DTO
 *
 * <p>Defines a tool that can be called by the AI model</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    /**
     * Tool type; currently only "function" is supported
     */
    @Builder.Default
    private String type = "function";

    /**
     * Function definition
     */
    private FunctionDefinition function;
}
