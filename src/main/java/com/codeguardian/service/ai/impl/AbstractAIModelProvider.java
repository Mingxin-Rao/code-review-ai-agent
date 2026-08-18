package com.codeguardian.service.ai.impl;

import com.codeguardian.service.ai.AIModelProvider;
import com.codeguardian.service.ai.config.ModelProviderConfig;
import com.codeguardian.service.ai.dto.AIModelRequest;
import com.codeguardian.service.ai.dto.AIModelResponse;
import com.codeguardian.service.ai.exception.AIModelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Abstract base class for AI model providers
 *
 * <p>Provides a common HTTP client and shared functionality; subclasses only implement provider-specific request building and response parsing</p>
 *
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractAIModelProvider implements AIModelProvider {

    protected final ModelProviderConfig config;
    protected final ObjectMapper objectMapper;
    private OkHttpClient httpClient;

    protected AbstractAIModelProvider(ModelProviderConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the HTTP client (singleton)
     */
    protected OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                    .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public AIModelResponse chat(AIModelRequest request) throws AIModelException {
        if (!isAvailable()) {
            log.warn("{} model provider is unavailable: baseUrl={}, apiKey={}",
                    getProviderName(),
                    config.getBaseUrl() != null ? "configured" : "not configured",
                    config.getApiKey() != null && !config.getApiKey().isEmpty() ? "configured" : "not configured");
            throw new AIModelException(getProviderName(), "Model provider is unavailable");
        }

        String model = getModelName(request);
        log.info("{} starting API call: model={}, baseUrl={}",
                getProviderName(), model, config.getBaseUrl());

        try {
            // build the request
            long buildStartTime = System.currentTimeMillis();
            Request httpRequest = buildRequest(request);
            long buildTime = System.currentTimeMillis() - buildStartTime;

            log.debug("{} request build complete: elapsed={}ms, URL={}",
                    getProviderName(), buildTime, httpRequest.url());

            // log request info
            log.debug("{} request URL: {}", getProviderName(), httpRequest.url());
            if (httpRequest.body() != null) {
                log.debug("{} request body built", getProviderName());
            }

            // execute the request
            long startTime = System.currentTimeMillis();
            log.info("{} sending HTTP request...", getProviderName());

            try (Response response = getHttpClient().newCall(httpRequest).execute()) {
                long responseTime = System.currentTimeMillis() - startTime;

                String responseBody = response.body().string();
                int responseBodyLength = responseBody != null ? responseBody.length() : 0;

                log.info("{} HTTP response received: status={}, responseTime={}ms, responseBodySize={} bytes",
                        getProviderName(), response.code(), responseTime, responseBodyLength);

                // print the full response body (for debugging)
                if (responseBody != null && !responseBody.isEmpty()) {
                    log.debug("{} HTTP response body (full): {}", getProviderName(), responseBody);
                    // if the response body is long, also print the first 2000 characters
                    if (responseBodyLength > 2000) {
                        log.debug("{} HTTP response body (first 2000 chars): {}",
                                getProviderName(), responseBody.substring(0, 2000));
                    }
                }

                if (!response.isSuccessful()) {
                    log.error("{} API call failed: status={}, first 1000 chars of response body={}",
                            getProviderName(),
                            response.code(),
                            responseBodyLength > 1000 ? responseBody.substring(0, 1000) : responseBody);
                    log.error("{} API call failed: full response body={}", getProviderName(), responseBody);
                    throw new AIModelException(
                            getProviderName(),
                            "API call failed",
                            response.code()
                    );
                }

                // parse the response
                long parseStartTime = System.currentTimeMillis();
                AIModelResponse aiResponse = parseResponse(responseBody, request);
                long parseTime = System.currentTimeMillis() - parseStartTime;

                // set metadata
                if (aiResponse.getMetadata() == null) {
                    aiResponse.setMetadata(AIModelResponse.ResponseMetadata.builder()
                            .responseTime(responseTime)
                            .build());
                } else {
                    aiResponse.getMetadata().setResponseTime(responseTime);
                }

                log.info("{} API call succeeded: totalElapsed={}ms (network={}ms, parse={}ms), model={}, usageTokens={}, contentLength={}",
                        getProviderName(),
                        responseTime,
                        responseTime - parseTime,
                        parseTime,
                        aiResponse.getModel(),
                        aiResponse.getUsageTokens() != null ? aiResponse.getUsageTokens() : 0,
                        aiResponse.getContent() != null ? aiResponse.getContent().length() : 0);

                if (aiResponse.getMetadata() != null && aiResponse.getMetadata().getRequestId() != null) {
                    log.debug("{} request ID: {}", getProviderName(), aiResponse.getMetadata().getRequestId());
                }

                return aiResponse;
            }
        } catch (AIModelException e) {
            log.error("{} API call business exception: {}", getProviderName(), e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            log.error("{} API call IO exception: {}", getProviderName(), e.getMessage(), e);
            throw new AIModelException(getProviderName(), "API call error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("{} API call unknown exception", getProviderName(), e);
            throw new AIModelException(getProviderName(), "API call error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return config != null
                && config.getEnabled()
                && StringUtils.hasText(config.getBaseUrl())
                && StringUtils.hasText(config.getApiKey());
    }

    /**
     * Build the HTTP request
     *
     * @param request the AI model request
     * @return the HTTP request object
     * @throws AIModelException thrown when building fails
     */
    protected abstract Request buildRequest(AIModelRequest request) throws AIModelException;

    /**
     * Parse the API response
     *
     * @param responseBody the response body
     * @param request the original request
     * @return the AI model response object
     * @throws AIModelException thrown when parsing fails
     */
    protected abstract AIModelResponse parseResponse(String responseBody, AIModelRequest request)
            throws AIModelException;

    /**
     * Create the JSON request body
     */
    protected RequestBody createJsonRequestBody(Object body) throws AIModelException {
        try {
            String json = objectMapper.writeValueAsString(body);
            return RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        } catch (Exception e) {
            throw new AIModelException(getProviderName(), "Failed to build request body", e);
        }
    }

    /**
     * Convert the message list to Map format (common helper)
     *
     * @param messages the message list
     * @return the message list in Map format
     */
    protected List<Map<String, String>> convertMessages(List<AIModelRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(msg -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", msg.getRole());
                    map.put("content", msg.getContent());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get the model name (falls back to the configured default model when not specified in the request)
     */
    protected String getModelName(AIModelRequest request) {
        return request.getModel() != null && !request.getModel().trim().isEmpty()
                ? request.getModel()
                : config.getDefaultModel();
    }

    /**
     * Build the base request headers
     */
    protected Request.Builder createBaseRequestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json");
    }
}
