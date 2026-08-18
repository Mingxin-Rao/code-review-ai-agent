package com.codeguardian.service.ai.util;

import com.codeguardian.entity.Finding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * AI response parsing utilities.
 * AI response parsing utility class.
 *
 * <p>Parses the JSON response returned by the AI into Finding entities.</p>
 * <p>Parses the JSON response returned by the model and converts it into {@link Finding} entities.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@UtilityClass
public class ResponseParser {

    /**
     * Parse an AI response into a list of {@link com.codeguardian.entity.Finding}.
     * Parse the AI response into a list of findings.
     *
     * @param response the AI response content
     * @param objectMapper the JSON object mapper
     * @return the list of findings
     */
    public List<Finding> parseFindings(String response, ObjectMapper objectMapper) {
        try {
            if (response == null || response.trim().isEmpty()) {
                log.warn("AI response is empty; nothing to parse");
                return new ArrayList<>();
            }

            log.debug("Parsing AI response; raw response length: {} chars", response.length());
            log.debug("Raw response content (first 1000 chars): {}",
                    response.length() > 1000 ? response.substring(0, 1000) : response);

            // clean the response text and extract the JSON part
            String jsonStr = cleanJsonResponse(response);

            log.debug("Cleaned JSON string length: {} chars", jsonStr.length());
            log.debug("Cleaned JSON string (first 1000 chars): {}",
                    jsonStr.length() > 1000 ? jsonStr.substring(0, 1000) : jsonStr);

            // check whether it is valid JSON
            if (!isValidJson(jsonStr)) {
                log.error("========== AI response is not valid JSON ==========");
                log.error("Response content (full): {}", response);
                log.error("Cleaned content (full): {}", jsonStr);
                log.error("========== end of response content ==========");
                return new ArrayList<>();
            }

            // parse the JSON
            List<FindingDTO> findingDTOs = objectMapper.readValue(
                    jsonStr,
                    new TypeReference<List<FindingDTO>>() {}
            );

            log.info("Successfully parsed AI response; found {} issue(s)", findingDTOs.size());

            // convert to entities
            List<Finding> findings = new ArrayList<>();
            for (FindingDTO dto : findingDTOs) {
                Finding finding = Finding.builder()
                        .severity(com.codeguardian.enums.SeverityEnum.fromName(dto.getSeverity() != null ? dto.getSeverity() : "MEDIUM").getValue())
                        .title(dto.getTitle() != null ? dto.getTitle() : "Untitled issue")
                        .location(dto.getLocation() != null ? dto.getLocation() : "Unknown location")
                        .startLine(dto.getStartLine())
                        .endLine(dto.getEndLine())
                        .description(dto.getDescription() != null ? dto.getDescription() : "")
                        .suggestion(dto.getSuggestion())
                        .diff(dto.getDiff())
                        .category(dto.getCategory())
                        .build();
                findings.add(finding);
            }

            return findings;

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("========== JSON parsing failed ==========");
            log.error("Error message: {}", e.getMessage());
            log.error("Raw response content (full): {}", response);
            log.error("========== end of response content ==========");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("========== Failed to parse AI response ==========");
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Raw response content (full): {}", response);
            log.error("========== end of response content ==========", e);
            return new ArrayList<>();
        }
    }

    /**
     * Clean the JSON response by stripping Markdown code-fence markers.
     * Clean the JSON response by removing markdown code-block markers.
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "";
        }
        
        String jsonStr = response.trim();

        // remove the leading markdown code-block marker
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7).trim();
        } else if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3).trim();
        }

        // remove the trailing markdown code-block marker
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3).trim();
        }

        // extract the JSON array part (if the response contains other text)
        // find the first '[' and the last ']'
        int firstBracket = jsonStr.indexOf('[');
        int lastBracket = jsonStr.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            jsonStr = jsonStr.substring(firstBracket, lastBracket + 1);
        }
        
        return jsonStr.trim();
    }
    
    /**
     * Check whether the string is valid JSON.
     * Check whether the string is in a valid JSON format.
     */
    private boolean isValidJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }

        String trimmed = str.trim();

        // check for a leading HTML tag (a common error case)
        if (trimmed.startsWith("<")) {
            log.warn("Response content starts with '<'; it may be HTML rather than JSON");
            return false;
        }

        // check whether it starts with a JSON array or object
        return trimmed.startsWith("[") || trimmed.startsWith("{");
    }

    /**
     * Finding DTO (used for JSON parsing).
     */
    private static class FindingDTO {
        public String severity;
        public String title;
        public String location;
        public Integer startLine;
        public Integer endLine;
        public String description;
        public String suggestion;
        public String diff;
        public String category;
        
        public String getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getLocation() { return location; }
        public Integer getStartLine() { return startLine; }
        public Integer getEndLine() { return endLine; }
        public String getDescription() { return description; }
        public String getSuggestion() { return suggestion; }
        public String getDiff() { return diff; }
        public String getCategory() { return category; }
    }
}
