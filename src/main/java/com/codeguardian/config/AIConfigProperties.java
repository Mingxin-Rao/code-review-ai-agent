package com.codeguardian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * AI configuration properties
 *
 * <p>Supports independent configuration for multiple AI model providers</p>
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AIConfigProperties {
    
    private Boolean enabled = true;
    
    /**
     * Default AI service provider: OPENAI, QWEN, DEEPSEEK
     */
    private String provider = "OPENAI";
    
    /**
     * Default timeout (seconds)
     */
    private Integer timeout = 60;
    
    /**
     * Default maximum number of retries
     */
    private Integer maxRetries = 3;
    
    /**
     * Independent configuration for each model provider
     * key: provider name (OPENAI, QWEN, DEEPSEEK)
     * value: configuration for that provider
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();
    
    /**
     * Configuration for a single model provider
     */
    @Data
    public static class ProviderConfig {
        /**
         * API base URL
     */
        private String baseUrl;
    
    /**
     * API key
     */
        private String apiKey;
    
    /**
         * Default model name
     */
        private String model;
        
        /**
         * Whether enabled
         */
        private Boolean enabled = true;
        
        /**
         * Connection timeout (seconds); falls back to the global timeout if not set
         */
        private Integer connectTimeout;
    
    /**
         * Read timeout (seconds); falls back to the global timeout if not set
     */
        private Integer readTimeout;
        
        /**
         * Write timeout (seconds); falls back to the global timeout if not set
         */
        private Integer writeTimeout;
    
    /**
         * Maximum number of retries; falls back to the global maxRetries if not set
     */
        private Integer maxRetries;
    }
    
    /**
     * Get the configuration for the specified provider
     */
    public ProviderConfig getProviderConfig(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            providerName = provider;
}
        return providers.get(providerName.toUpperCase());
    }
    
    /**
     * Check whether the specified provider is configured
     */
    public boolean isProviderConfigured(String providerName) {
        ProviderConfig config = getProviderConfig(providerName);
        return config != null 
                && config.getEnabled() != null && config.getEnabled()
                && config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                && config.getApiKey() != null && !config.getApiKey().trim().isEmpty();
    }
}
