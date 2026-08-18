package com.codeguardian.service.ai.config;

import lombok.Builder;
import lombok.Data;

/**
 * Model provider configuration
 * 
 * @since 1.0.0
 */
@Data
@Builder
public class ModelProviderConfig {
    private String providerName;
    private String baseUrl;
    private String apiKey;
    private String defaultModel;
    private Boolean enabled;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;
    private Integer maxRetries;
}

