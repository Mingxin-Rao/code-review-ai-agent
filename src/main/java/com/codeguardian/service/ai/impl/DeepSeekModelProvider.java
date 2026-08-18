package com.codeguardian.service.ai.impl;

import com.codeguardian.service.ai.config.ModelProviderConfig;
import com.codeguardian.service.ai.dto.AIModelRequest;
import com.codeguardian.service.ai.exception.AIModelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * DeepSeek model provider implementation
 *
 * <p>Implements the call logic for the DeepSeek API (OpenAI-compatible)
 * Supports both local Ollama deployments and the cloud DeepSeek API</p>
 *
 * @since 1.0.0
 */
@Slf4j
public class DeepSeekModelProvider extends OpenAICompatibleProvider {

    private static final String PROVIDER_NAME = "DEEPSEEK";

    public DeepSeekModelProvider(ModelProviderConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected Request buildRequest(AIModelRequest request) throws AIModelException {
        String model = getModelName(request);

        log.debug("{} building DeepSeek request: model={}, temperature={}, messagesCount={}",
                getProviderName(), model, request.getTemperature(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // build the request body (OpenAI format)
        Map<String, Object> requestBody = buildOpenAIRequestBody(request, model);

        // determine whether this is a local Ollama (baseUrl usually contains localhost or 127.0.0.1)
        boolean isLocalOllama = config.getBaseUrl() != null
                && (config.getBaseUrl().contains("localhost")
                    || config.getBaseUrl().contains("127.0.0.1"));

        String url;
        if (isLocalOllama) {
            // Ollama uses the /api/chat endpoint (OpenAI-compatible mode)
            // if baseUrl already contains the full path, use it directly; otherwise append
            if (config.getBaseUrl().endsWith("/api/chat") || config.getBaseUrl().endsWith("/v1/chat/completions")) {
                url = config.getBaseUrl();
            } else {
                // default to /v1/chat/completions (Ollama supports OpenAI-compatible mode)
                url = config.getBaseUrl() + "/v1/chat/completions";
            }
            log.debug("{} detected local Ollama service", getProviderName());
        } else {
            // cloud DeepSeek API
            url = config.getBaseUrl() + API_ENDPOINT;
        }

        log.debug("{} request URL: {}", getProviderName(), url);

        RequestBody body = createJsonRequestBody(requestBody);

        Request.Builder requestBuilder = createBaseRequestBuilder(url);

        // local Ollama usually does not require an API Key, but cloud DeepSeek does
        if (!isLocalOllama && config.getApiKey() != null && !config.getApiKey().trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        } else if (isLocalOllama) {
            log.debug("{} local Ollama service, skipping API Key authentication", getProviderName());
        }

        return requestBuilder.post(body).build();
    }

    @Override
    public boolean isAvailable() {
        // for a local Ollama service, apiKey may be empty
        boolean isLocalOllama = config.getBaseUrl() != null
                && (config.getBaseUrl().contains("localhost")
                    || config.getBaseUrl().contains("127.0.0.1"));

        if (isLocalOllama) {
            // a local service only needs baseUrl and the enabled status
            return config != null
                    && config.getEnabled()
                    && StringUtils.hasText(config.getBaseUrl());
        } else {
            // a cloud service requires apiKey
            return config != null
                    && config.getEnabled()
                    && StringUtils.hasText(config.getBaseUrl())
                    && StringUtils.hasText(config.getApiKey());
        }
    }

    @Override
    public String[] getSupportedModels() {
        return new String[]{
            "deepseek-r1:8b",
            "deepseek-chat",
            "deepseek-coder",
            "deepseek-chat-32k"
        };
    }
}
