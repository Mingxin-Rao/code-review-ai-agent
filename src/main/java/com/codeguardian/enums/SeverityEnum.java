package com.codeguardian.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Severity level enum.
 *
 * @date 2025/12/30
 */
@Getter
@AllArgsConstructor
public enum SeverityEnum {

    /**
     * Critical
     */
    CRITICAL(0, "Critical"),

    /**
     * High
     */
    HIGH(1, "High"),

    /**
     * Medium
     */
    MEDIUM(2, "Medium"),

    /**
     * Low
     */
    LOW(3, "Low");

    /**
     * enum value
     */
    private Integer value;

    /**
     * enum description
     */
    private String desc;

    public static SeverityEnum fromName(String name) {
        if (name == null) return MEDIUM;
        String n = name.toUpperCase();
        for (SeverityEnum e : values()) {
            if (e.name().equals(n)) return e;
        }
        return MEDIUM;
    }

    public static SeverityEnum fromValue(Integer value) {
        if (value == null) return MEDIUM;
        for (SeverityEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return MEDIUM;
    }
}
