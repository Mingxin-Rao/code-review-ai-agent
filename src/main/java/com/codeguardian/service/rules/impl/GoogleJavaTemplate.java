package com.codeguardian.service.rules.impl;

import com.codeguardian.service.rules.AbstractJsonRuleTemplate;
import org.springframework.stereotype.Component;

/**
 * Google Java Style Guide ruleset
 */
@Component
public class GoogleJavaTemplate extends AbstractJsonRuleTemplate {

    @Override
    protected String getJsonFilePath() {
        return "rules/google-java.json";
    }

    @Override
    public boolean supports(String language) {
        return "JAVA".equalsIgnoreCase(language);
    }
}
