package com.codeguardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Code-review AI agent main application.
 * 
 * @version 1.0.0
 */
@SpringBootApplication(exclude = {
    org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
public class CodeReviewApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CodeReviewApplication.class, args);
    }
}


