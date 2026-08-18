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
 * Alibaba Cloud Tongyi Qianwen (Qwen) model provider implementation
 *
 * <p>Implements the call logic for the Alibaba Cloud DashScope API</p>
 *
 * @since 1.0.0
 */
@Slf4j
public class QwenModelProvider extends AbstractAIModelProvider {

    private static final String PROVIDER_NAME = "QWEN";
    private static final String API_ENDPOINT = "/api/v1/services/aigc/text-generation/generation";

    public QwenModelProvider(ModelProviderConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected Request buildRequest(AIModelRequest request) throws AIModelException {
        String model = getModelName(request);

        log.debug("{} building Qwen-format request: model={}, temperature={}, messagesCount={}",
                getProviderName(), model, request.getTemperature(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // determine whether to use compatible mode (baseUrl contains compatible-mode)
        boolean useCompatibleMode = config.getBaseUrl() != null
                && config.getBaseUrl().contains("compatible-mode");

        String url;
        Map<String, Object> requestBody;

        if (useCompatibleMode) {
            // compatible mode: use the OpenAI format
            log.debug("{} using compatible mode (OpenAI format)", getProviderName());
            // baseUrl may already contain the full path, or only the base path
            if (config.getBaseUrl().endsWith("/chat/completions")) {
                url = config.getBaseUrl();
            } else {
                // if baseUrl is https://dashscope.aliyuncs.com/compatible-mode/v1
                // then /chat/completions needs to be appended
                url = config.getBaseUrl() + "/chat/completions";
            }
            requestBody = buildOpenAICompatibleRequestBody(request, model);
        } else {
            // native mode: use the DashScope format
            log.debug("{} using native mode (DashScope format)", getProviderName());
            url = config.getBaseUrl() + API_ENDPOINT;
            requestBody = buildQwenRequestBody(request, model);
        }

        log.debug("{} request URL: {}", getProviderName(), url);

        RequestBody body = createJsonRequestBody(requestBody);

        Request.Builder requestBuilder = createBaseRequestBuilder(url);

        // compatible mode uses Bearer authentication; native mode uses X-DashScope-API-Key
        if (useCompatibleMode) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        } else {
            requestBuilder.header("X-DashScope-API-Key", config.getApiKey());
        }

        return requestBuilder.post(body).build();
    }

    @Override
    protected AIModelResponse parseResponse(String responseBody, AIModelRequest request)
            throws AIModelException {
        try {
            log.debug("{} starting to parse response, response body length: {} chars",
                    getProviderName(), responseBody != null ? responseBody.length() : 0);

            JsonNode jsonNode = objectMapper.readTree(responseBody);

            // determine whether compatible mode is used (check the response format)
            boolean useCompatibleMode = jsonNode.has("choices") && !jsonNode.has("output");

            if (useCompatibleMode) {
                // compatible mode: parse using the OpenAI format
                log.debug("{} parsing with compatible mode (OpenAI format)", getProviderName());
                return parseOpenAICompatibleResponse(jsonNode);
            } else {
                // native mode: parse using the DashScope format
                log.debug("{} parsing with native mode (DashScope format)", getProviderName());
                return parseQwenNativeResponse(jsonNode);
            }
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

    @Override
    public String[] getSupportedModels() {
        return new String[]{"qwen3-max", "qwen-turbo", "qwen-plus", "qwen-max", "qwen-max-longcontext"};
    }

    /**
     * Build the OpenAI-compatible request body (for compatible mode)
     */
    private Map<String, Object> buildOpenAICompatibleRequestBody(AIModelRequest request, String model) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", convertMessages(request.getMessages()));
        requestBody.put("temperature", request.getTemperature());

        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }

        return requestBody;
    }

    /**
     * Build the DashScope native-format request body
     */
    private Map<String, Object> buildQwenRequestBody(AIModelRequest request, String model) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", Map.of("messages", convertMessages(request.getMessages())));
        requestBody.put("parameters", Map.of("temperature", request.getTemperature()));
        return requestBody;
    }

    /**
     * Parse the OpenAI-compatible response
     */
    private AIModelResponse parseOpenAICompatibleResponse(JsonNode jsonNode) throws AIModelException {
        JsonNode choices = jsonNode.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new AIModelException(getProviderName(), "No choices in response");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        String content = message.path("content").asText();
        String finishReason = firstChoice.path("finish_reason").asText();

        JsonNode usage = jsonNode.path("usage");
        Integer usageTokens = usage.path("total_tokens").asInt(0);

        String requestId = jsonNode.path("id").asText("");

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
     * Parse the DashScope native-format response
     */
    private AIModelResponse parseQwenNativeResponse(JsonNode jsonNode) throws AIModelException {
        // Qwen response format: output.choices[0].message.content
        JsonNode output = jsonNode.path("output");
        if (output.isMissingNode()) {
            log.error("{} malformed response: missing output field, response body={}",
                    getProviderName(), jsonNode.toString());
            throw new AIModelException(getProviderName(),
                    "No output field in response: " + jsonNode.toString());
        }

        JsonNode choices = output.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            log.error("{} malformed response: choices is empty or not an array, response body={}",
                    getProviderName(), jsonNode.toString());
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

        String requestId = jsonNode.path("request_id").asText("");
        log.debug("{} native-mode response parsed successfully: model={}, contentLength={}, usageTokens={}, requestId={}",
                getProviderName(),
                jsonNode.path("model").asText(),
                content != null ? content.length() : 0,
                usageTokens,
                requestId);

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
}
