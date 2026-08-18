package com.codeguardian.service.ai.config;

import com.codeguardian.config.AIConfigProperties;
import com.codeguardian.enums.ModelProviderEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * AI model configuration manager
 *
 * <p>Manages configuration for multiple AI model providers, loadable from config files or environment variables</p>
 *
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AIModelConfigManager {

    private final AIConfigProperties aiConfigProperties;
    private final Map<String, ModelProviderConfig> configCache = new HashMap<>();

    /**
     * Get the default model provider configuration
     */
    public ModelProviderConfig getDefaultConfig() {
        return getConfig(aiConfigProperties.getProvider());
    }

    /**
     * Get configuration by provider name
     */
    public ModelProviderConfig getConfig(String providerName) {
        String normalizedProviderName = providerName;
        if (normalizedProviderName == null || normalizedProviderName.trim().isEmpty()) {
            normalizedProviderName = aiConfigProperties.getProvider();
        }

        ModelProviderEnum provider = ModelProviderEnum.from(normalizedProviderName)
                .orElse(ModelProviderEnum.OPENAI);
        return configCache.computeIfAbsent(provider.getCode(), k -> buildConfig(provider));
    }

    /**
     * Build the model provider configuration
     */
    private ModelProviderConfig buildConfig(ModelProviderEnum provider) {
        String upperProviderName = provider.getCode();

        // get this provider's configuration from the config
        AIConfigProperties.ProviderConfig providerConfig =
                aiConfigProperties.getProviderConfig(upperProviderName);

        ModelProviderConfig.ModelProviderConfigBuilder builder = ModelProviderConfig.builder()
                .providerName(upperProviderName);

        boolean globallyEnabled = aiConfigProperties.getEnabled() == null || aiConfigProperties.getEnabled();

        if (providerConfig != null) {
            // use this provider's own configuration
            builder.baseUrl(providerConfig.getBaseUrl())
                    .apiKey(providerConfig.getApiKey())
                    .defaultModel(providerConfig.getModel())
                    .enabled(globallyEnabled && (providerConfig.getEnabled() != null ? providerConfig.getEnabled() : true))
                    .connectTimeout(providerConfig.getConnectTimeout() != null
                            ? providerConfig.getConnectTimeout()
                            : aiConfigProperties.getTimeout())
                    .readTimeout(providerConfig.getReadTimeout() != null
                            ? providerConfig.getReadTimeout()
                            : aiConfigProperties.getTimeout())
                    .writeTimeout(providerConfig.getWriteTimeout() != null
                            ? providerConfig.getWriteTimeout()
                            : aiConfigProperties.getTimeout())
                    .maxRetries(providerConfig.getMaxRetries() != null
                            ? providerConfig.getMaxRetries()
                            : aiConfigProperties.getMaxRetries());
        } else {
            // if there is no configuration, use defaults and log a warning
            log.warn("Provider {} is not configured in the config file; using default values", upperProviderName);
            builder.baseUrl("")
                    .apiKey("")
                    .enabled(false)
                    .connectTimeout(aiConfigProperties.getTimeout())
                    .readTimeout(aiConfigProperties.getTimeout())
                    .writeTimeout(aiConfigProperties.getTimeout())
                    .maxRetries(aiConfigProperties.getMaxRetries());
        }

        // set the default model per provider (if not configured)
        String defaultModel = providerConfig != null ? providerConfig.getModel() : null;
        if (!StringUtils.hasText(defaultModel)) {
            defaultModel = provider.getDefaultModel();
            builder.defaultModel(defaultModel);
        }

        ModelProviderConfig config = builder.build();
        log.info("Built model config: provider={}, baseUrl={}, model={}, enabled={}",
                config.getProviderName(),
                config.getBaseUrl(),
                config.getDefaultModel(),
                config.getEnabled());

        return config;
    }

    /**
     * Get the list of all configured providers
     */
    public java.util.List<ModelProviderEnum> getConfiguredProviders() {
        return aiConfigProperties.getProviders().entrySet().stream()
                .filter(entry -> {
                    if (entry.getValue() == null
                            || entry.getValue().getEnabled() == null
                            || !entry.getValue().getEnabled()
                            || !StringUtils.hasText(entry.getValue().getBaseUrl())) {
                        return false;
                    }

                    // for a local DeepSeek (Ollama), apiKey may be empty
                    ModelProviderEnum provider = ModelProviderEnum.from(entry.getKey()).orElse(null);
                    if (provider == ModelProviderEnum.DEEPSEEK) {
                        String baseUrl = entry.getValue().getBaseUrl();
                        boolean isLocalOllama = baseUrl != null
                                && (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1"));
                        if (isLocalOllama) {
                            // a local service does not require apiKey
                            return true;
                        }
                    }

                    // other providers require apiKey
                    return StringUtils.hasText(entry.getValue().getApiKey());
                })
                .map(entry -> ModelProviderEnum.from(entry.getKey()).orElse(null))
                .filter(provider -> provider != null)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Clear the configuration cache
     */
    public void clearCache() {
        configCache.clear();
        log.info("AI model configuration cache cleared");
    }
}
