package com.codeguardian.service.ai;

import com.codeguardian.service.ai.dto.AIModelRequest;
import com.codeguardian.service.ai.dto.AIModelResponse;
import com.codeguardian.service.ai.exception.AIModelException;

/**
 * AI model provider interface
 *
 * <p>Defines a unified interface for calling AI models; different model providers implement this interface.
 * Uses the strategy pattern to allow flexible extension with new AI models.</p>
 * 
 * @since 1.0.0
 */
public interface AIModelProvider {
    
    /**
     * Get the provider name
     *
     * @return the provider name, e.g. OPENAI, QWEN, DEEPSEEK
     */
    String getProviderName();

    /**
     * Call the AI model to generate text
     *
     * @param request the AI model request object
     * @return the AI model response object
     * @throws AIModelException thrown when the call fails
     */
    AIModelResponse chat(AIModelRequest request) throws AIModelException;

    /**
     * Check whether the provider is available
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    /**
     * Get the list of models supported by the provider
     *
     * @return the list of model names
     */
    String[] getSupportedModels();
}

