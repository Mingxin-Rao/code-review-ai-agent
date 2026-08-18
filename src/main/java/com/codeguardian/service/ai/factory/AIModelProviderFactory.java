package com.codeguardian.service.ai.factory;

import com.codeguardian.service.ai.AIModelProvider;
import com.codeguardian.service.ai.config.ModelProviderConfig;
import com.codeguardian.service.ai.exception.AIModelException;
import com.codeguardian.service.ai.impl.DeepSeekModelProvider;
import com.codeguardian.service.ai.impl.OpenAIModelProvider;
import com.codeguardian.service.ai.impl.QwenModelProvider;
import com.codeguardian.enums.ModelProviderEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI model provider factory
 *
 * <p>Creates and manages AI model provider instances; uses a singleton pattern to ensure a single instance per provider</p>
 * 
 * @since 1.0.0
 */
@Component
@Slf4j
public class AIModelProviderFactory {
    
    private final ObjectMapper objectMapper;
    private final Map<String, AIModelProvider> providerCache = new ConcurrentHashMap<>();
    
    public AIModelProviderFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Create or retrieve a model provider instance
     *
     * @param config the model provider configuration
     * @return the model provider instance
     * @throws AIModelException thrown when the provider type is unsupported
     */
    public AIModelProvider createProvider(ModelProviderConfig config) throws AIModelException {
        if (config == null || !config.getEnabled()) {
            throw new AIModelException("UNKNOWN", "Configuration is empty or disabled");
        }

        ModelProviderEnum provider = ModelProviderEnum.from(config.getProviderName())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported AI model provider: " + config.getProviderName()));
        String cacheKey = provider.getCode() + ":" + config.getBaseUrl();
        
        // get from cache
        return providerCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating AI model provider: {}", provider.getCode());
            return doCreateProvider(provider, config);
        });
    }
    
    /**
     * Create a provider instance by provider name
     */
    private AIModelProvider doCreateProvider(ModelProviderEnum provider, ModelProviderConfig config) {
        switch (provider) {
            case OPENAI:
                return new OpenAIModelProvider(config, objectMapper);
            case QWEN:
                return new QwenModelProvider(config, objectMapper);
            case DEEPSEEK:
                return new DeepSeekModelProvider(config, objectMapper);
            default:
                throw new IllegalArgumentException("Unsupported AI model provider: " + provider.getCode());
        }
    }
    
    /**
     * Clear the cache
     */
    public void clearCache() {
        providerCache.clear();
        log.info("AI model provider cache cleared");
    }

    /**
     * Get the cache size
     */
    public int getCacheSize() {
        return providerCache.size();
    }
}
