package com.codeguardian.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import lombok.extern.slf4j.Slf4j;
import com.codeguardian.enums.ModelProviderEnum;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI configuration class
 * 
 * <p>Configures multiple AI model providers using Spring AI's unified interface and auto-configuration features</p>
 * 
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class SpringAIConfig {

    private final Environment environment;

    public SpringAIConfig(Environment environment) {
        this.environment = environment;
    }
    
    @Value("${spring.ai.qwen.api-key:${app.rag.qwen.api-key:}}")
    private String qwenApiKey;
    
    @Value("${spring.ai.qwen.base-url:${app.rag.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode}}")
    private String qwenBaseUrl;
    
    @Value("${spring.ai.qwen.chat.options.model:${app.rag.qwen.chat.options.model:qwen3-max}}")
    private String qwenModel;
    
    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;
    
    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;
    
    @Value("${spring.ai.openai.chat.options.model:gpt-3.5-turbo}")
    private String openAiModel;
    
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.ollama.chat.options.model:deepseek-r1:8b}")
    private String ollamaModel;
    
    /**
     * Inject the Ollama ChatModel bean auto-configured by Spring AI
     * Use required=false so that a missing bean does not cause an error
     */
    @Autowired(required = false)
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;
    
    /**
     * OpenAI ChatModel Bean
     * Created only when a non-empty API key is configured, to avoid startup failure
     */
    @Bean(name = "openAiChatModel")
    @ConditionalOnExpression("!'${spring.ai.openai.api-key:}'.isEmpty()")
    public ChatModel openAiChatModel() {
        
        log.info("Initializing OpenAI ChatModel: baseUrl={}, model={}", openAiBaseUrl, openAiModel);
        
        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrl, openAiApiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(openAiModel)
                .withTemperature(0.3)
                .build();
        
        return new OpenAiChatModel(openAiApi, options);
    }
    
    /**
     * Qwen ChatModel bean (uses the OpenAI-compatible interface)
     * Note: Spring AI has no native Qwen support, so we use the OpenAI-compatible mode
     */
    @Bean(name = "qwenChatModel")
    @ConditionalOnExpression("!('${spring.ai.qwen.api-key:${app.rag.qwen.api-key:}}').isEmpty()")
    public ChatModel qwenChatModel() {
        log.info("Initializing Qwen ChatModel: baseUrl={}, model={}", qwenBaseUrl, qwenModel);
        OpenAiApi qwenApi = new OpenAiApi(qwenBaseUrl, qwenApiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(qwenModel)
                .withTemperature(0.3)
                .build();
        
        log.info("Qwen ChatModel configured: baseUrl={}, model={}, final API endpoint: {}/v1/chat/completions",
                qwenBaseUrl, qwenModel, qwenBaseUrl);
        return new OpenAiChatModel(qwenApi, options);
    }
    
    /**
     * Default ChatModel (uses Qwen; falls back to OpenAI if unavailable)
     * Use @Lazy to avoid a circular dependency
     */
    @Bean
    @Primary
    public ChatModel defaultChatModel(
            @Lazy @Qualifier("qwenChatModel") ObjectProvider<ChatModel> qwenChatModelProvider,
            @Lazy @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAiChatModelProvider) {
        ChatModel qwenChatModel = qwenChatModelProvider.getIfAvailable();
        ChatModel openAiChatModel = openAiChatModelProvider.getIfAvailable();

        if (qwenChatModel != null) {
            log.info("Using Qwen as the default ChatModel");
            return qwenChatModel;
        }
        if (openAiChatModel != null) {
            log.info("Using OpenAI as the default ChatModel");
            return openAiChatModel;
        }
        log.warn("No ChatModel available; returning null");
        return null;
    }
    

    
    /**
     * ChatModel map used to look up the corresponding ChatModel by provider name
     * Use @Lazy to avoid a circular dependency
     */
    @Bean
    public Map<String, ChatModel> chatModelMap(
            @Lazy @Qualifier("qwenChatModel") ObjectProvider<ChatModel> qwenChatModelProvider,
            @Lazy @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAiChatModelProvider) {
        Map<String, ChatModel> modelMap = new HashMap<>();
        ChatModel qwenChatModel = qwenChatModelProvider.getIfAvailable();
        ChatModel openAiChatModel = openAiChatModelProvider.getIfAvailable();
        
        // Custom OpenAI ChatModel (created only when an API key is present)
        if (openAiChatModel != null) {
            modelMap.put(ModelProviderEnum.OPENAI.getCode(), openAiChatModel);
            log.info("Registered OpenAI ChatModel (custom configuration)");
        }
        
        // Use the Ollama ChatModel auto-configured by Spring AI
        if (ollamaChatModel != null && isDeepSeekConfigured()) {
            modelMap.put(ModelProviderEnum.DEEPSEEK.getCode(), ollamaChatModel);
            log.info("Registered Ollama ChatModel (DeepSeek, from Spring AI auto-configuration)");
        }
        
        // Custom Qwen ChatModel
        if (qwenChatModel != null) {
            modelMap.put(ModelProviderEnum.QWEN.getCode(), qwenChatModel);
            log.info("Registered Qwen ChatModel (custom configuration)");
        }
        
        log.info("Registered ChatModels: {}", modelMap.keySet());
        if (modelMap.isEmpty()) {
            log.warn("Warning: no ChatModel available; the application may not work correctly");
        }
        return modelMap;
    }

    private boolean isDeepSeekConfigured() {
        String baseUrl = environment.getProperty("spring.ai.ollama.base-url");
        if (StringUtils.hasText(baseUrl)) {
            return true;
        }
        String appBaseUrl = environment.getProperty("app.rag.ollama.base-url");
        return StringUtils.hasText(appBaseUrl);
    }
}
