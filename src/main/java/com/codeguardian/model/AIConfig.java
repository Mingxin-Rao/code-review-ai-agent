package com.codeguardian.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI config
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIConfig {
    
    /**
     * AI service provider: OPENAI, CLAUDE, LOCAL
     */
    @Builder.Default
    private String provider = "OPENAI";
    
    /**
     * API key
     */
    private String apiKey;
    
    /**
     * API endpoint URL
     */
    private String apiUrl;
    
    /**
     * Model name
     */
    @Builder.Default
    private String model = "gpt-4";
    
    /**
     * Temperature parameter (0-1)
     */
    @Builder.Default
    private double temperature = 0.3;
    
    /**
     * Maximum number of tokens
     */
    @Builder.Default
    private int maxTokens = 2000;
}


