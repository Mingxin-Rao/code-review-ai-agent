package com.codeguardian.service.rules;

import com.codeguardian.dto.CustomRuleDTO;
import com.codeguardian.entity.Finding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standards-based rule review engine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final List<RuleTemplate> ruleTemplates;

    public List<Finding> reviewWithTemplate(String code, String language, String templateName) {
        String lang = normalize(language);
        String tpl = templateName != null ? templateName.toUpperCase() : "";
        List<RuleDefinition> rules = new ArrayList<>();

        boolean found = false;
        for (RuleTemplate template : ruleTemplates) {
            if (template.getName().equalsIgnoreCase(tpl) && template.supports(lang)) {
                rules.addAll(template.getRules());
                found = true;
                break;
            }
        }

        if (!found) {
            log.warn("Unknown or unmatched ruleset: {} / {}, trying to find generic language support", templateName, language);
            // try matching by language only
            for (RuleTemplate template : ruleTemplates) {
                if (template.supports(lang) && (tpl.isEmpty() || template.getName().contains(tpl))) {
                     rules.addAll(template.getRules());
                }
            }
        }

        return applyRules(code, rules);
    }

    public List<Finding> reviewWithCustom(String code, List<CustomRuleDTO> customRules) {
        List<RuleDefinition> rules = new ArrayList<>();
        if (customRules != null) {
            for (CustomRuleDTO dto : customRules) {
                if (dto.getPattern() == null || dto.getPattern().isEmpty()) continue;
                rules.add(RuleDefinition.builder()
                        .name(dto.getName() != null ? dto.getName() : "Custom Rule")
                        .description(dto.getPoint() != null ? dto.getPoint() : "")
                        .pattern(dto.getPattern())
                        .severity(dto.getSeverity() != null ? dto.getSeverity().toUpperCase() : "MEDIUM")
                        .weight(dto.getWeight() != null ? dto.getWeight() : 50)
                        .suggestion("Suggestion: follow the standard and fix the issue above.")
                        .build());
            }
        }
        return applyRules(code, rules);
    }

    private List<Finding> applyRules(String code, List<RuleDefinition> rules) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = code != null ? code.split("\r?\n") : new String[0];

        for (RuleDefinition rule : rules) {
            try {
                Pattern p = Pattern.compile(rule.getPattern(), Pattern.MULTILINE);
                Matcher m = p.matcher(code);
                while (m.find()) {
                    int start = m.start();
                    int line = computeLineNumber(code, start);
                    // String matchedLine = line > 0 && line <= lines.length ? lines[line - 1] : "";
                    findings.add(Finding.builder()
                            .severity(com.codeguardian.enums.SeverityEnum.fromName(rule.getSeverity()).getValue())
                            .title(rule.getName())
                            .location("Line " + line)
                            .startLine(line)
                            .endLine(line) // for simplicity, treat as a single line for now
                            .description(rule.getDescription())
                            .suggestion(rule.getSuggestion())
                            .category("CODE_STYLE")
                            .diff(null)
                            .build());
                }
            } catch (Exception e) {
                log.warn("Failed to apply rule: {}", rule.getName(), e);
            }
        }

        return findings;
    }

    private int computeLineNumber(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private String normalize(String lang) {
        if (lang == null) return "";
        String l = lang.toUpperCase();
        if (l.startsWith("JAVASCRIPT") || l.equals("JS")) return "JS";
        if (l.startsWith("TYPESCRIPT") || l.equals("TS")) return "TS";
        if (l.startsWith("JAVA")) return "JAVA";
        if (l.startsWith("PY")) return "PY";
        return l;
    }
}
