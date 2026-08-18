package com.codeguardian.service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI model request DTO
 *
 * <p>Wraps the request parameters required to call an AI model</p>
 * 
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIModelRequest {
    
    /**
     * Model name
     */
    private String model;

    /**
     * Message list
     */
    private List<Message> messages;

    /**
     * Temperature (0-2), controls output randomness
     */
    @Builder.Default
    private Double temperature = 0.3;

    /**
     * Maximum number of tokens
     */
    private Integer maxTokens;

    /**
     * Additional extended parameters
     */
    private Map<String, Object> extraParams;

    /**
     * Message object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * Role: user, assistant, system
         */
        private String role;

        /**
         * Message content
         */
        private String content;
    }
}

