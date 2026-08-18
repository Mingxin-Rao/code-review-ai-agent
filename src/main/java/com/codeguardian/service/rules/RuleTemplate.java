package com.codeguardian.service.rules;

import java.util.List;

/**
 * Ruleset template interface
 */
public interface RuleTemplate {

    /**
     * Get the template name (e.g. ALIBABA, GOOGLE)
     */
    String getName();

    /**
     * Get the template description
     */
    String getDescription();

    /**
     * Get the rule list
     */
    List<RuleDefinition> getRules();

    /**
     * Whether this language is supported
     */
    boolean supports(String language);
}