package com.codeguardian.service.ai.impl;

import com.codeguardian.service.ai.config.ModelProviderConfig;
import com.codeguardian.service.ai.dto.AIModelRequest;
import com.codeguardian.service.ai.dto.AIModelResponse;
import com.codeguardian.service.ai.exception.AIModelException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for OpenAI-compatible model providers
 *
 * <p>Provides a common implementation for OpenAI-format APIs, suitable for OpenAI, DeepSeek, and other providers compatible with the OpenAI format</p>
 *
 * @since 1.0.0
 */
@Slf4j
public abstract class OpenAICompatibleProvider extends AbstractAIModelProvider {

    protected static final String API_ENDPOINT = "/v1/chat/completions";

    protected OpenAICompatibleProvider(ModelProviderConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    protected Request buildRequest(AIModelRequest request) throws AIModelException {
        String model = getModelName(request);

        log.debug("{} building OpenAI-format request: model={}, temperature={}, messagesCount={}",
                getProviderName(), model, request.getTemperature(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // build the request body (OpenAI format)
        Map<String, Object> requestBody = buildOpenAIRequestBody(request, model);

        String url = config.getBaseUrl() + API_ENDPOINT;
        log.debug("{} request URL: {}", getProviderName(), url);

        RequestBody body = createJsonRequestBody(requestBody);

        return createBaseRequestBuilder(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(body)
                .build();
    }

    @Override
    protected AIModelResponse parseResponse(String responseBody, AIModelRequest request)
            throws AIModelException {
        try {
            log.debug("{} starting to parse OpenAI-format response, response body length: {} chars",
                    getProviderName(), responseBody != null ? responseBody.length() : 0);

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            AIModelResponse response = parseOpenAIFormatResponse(jsonNode);

            log.debug("{} response parsed successfully: model={}, contentLength={}, usageTokens={}",
                    getProviderName(),
                    response.getModel(),
                    response.getContent() != null ? response.getContent().length() : 0,
                    response.getUsageTokens());

            return response;
        } catch (AIModelException e) {
            log.error("{} response-parsing business exception: {}", getProviderName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("{} failed to parse response: first 500 chars of response body={}",
                    getProviderName(),
                    responseBody != null && responseBody.length() > 500
                            ? responseBody.substring(0, 500)
                            : responseBody,
                    e);
            throw new AIModelException(getProviderName(), "Failed to parse response", e);
        }
    }

    /**
     * Build the OpenAI-format request body
     */
    protected Map<String, Object> buildOpenAIRequestBody(AIModelRequest request, String model) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", convertMessages(request.getMessages()));
        requestBody.put("temperature", request.getTemperature());

        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }

        // add extended parameters
        if (request.getExtraParams() != null) {
            requestBody.putAll(request.getExtraParams());
        }

        return requestBody;
    }

    /**
     * Parse the OpenAI-format response
     */
    protected AIModelResponse parseOpenAIFormatResponse(JsonNode jsonNode) throws AIModelException {
        // parse choices
        JsonNode choices = jsonNode.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new AIModelException(getProviderName(),
                    "No choices in response: " + jsonNode.toString());
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        String content = message.path("content").asText();
        String finishReason = firstChoice.path("finish_reason").asText();

        // parse usage
        JsonNode usage = jsonNode.path("usage");
        Integer usageTokens = usage.path("total_tokens").asInt(0);

        // parse requestId (field name may differ across providers)
        String requestId = extractRequestId(jsonNode);

        return AIModelResponse.builder()
                .content(content)
                .model(jsonNode.path("model").asText())
                .usageTokens(usageTokens)
                .finishReason(finishReason)
                .metadata(AIModelResponse.ResponseMetadata.builder()
                        .requestId(requestId)
                        .build())
                .build();
    }

    /**
     * Extract the request ID (subclasses can override to support different field names)
     */
    protected String extractRequestId(JsonNode jsonNode) {
        // prefer the "id" field (OpenAI format)
        if (jsonNode.has("id")) {
            return jsonNode.path("id").asText();
        }
        // try the "request_id" field (some compatible formats)
        if (jsonNode.has("request_id")) {
            return jsonNode.path("request_id").asText();
        }
        return "";
    }
}
