package com.codeguardian.service.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI model response DTO
 *
 * <p>Wraps the result of an AI model call</p>
 * 
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIModelResponse {
    
    /**
     * Response content
     */
    private String content;

    /**
     * Name of the model used
     */
    private String model;

    /**
     * Number of tokens used
     */
    private Integer usageTokens;

    /**
     * Finish reason
     */
    private String finishReason;
    
    /**
     * Response metadata
     */
    private ResponseMetadata metadata;
    
    /**
     * Response metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetadata {
        /**
         * Request ID
         */
        private String requestId;

        /**
         * Response time (milliseconds)
         */
        private Long responseTime;

        /**
         * Additional metadata
         */
        private java.util.Map<String, Object> extra;
    }
}

