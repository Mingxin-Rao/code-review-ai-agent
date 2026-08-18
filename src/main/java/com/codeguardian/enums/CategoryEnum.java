package com.codeguardian.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Issue category enum.
 *
 * @date 2025/12/30
 */
@Getter
@AllArgsConstructor
public enum CategoryEnum {

    /**
     * Security
     */
    SECURITY(0, "Security"),

    /**
     * Performance
     */
    PERFORMANCE(1, "Performance"),

    /**
     * Bug
     */
    BUG(2, "Bug"),

    /**
     * Code Style
     */
    CODE_STYLE(3, "Code Style"),

    /**
     * Maintainability
     */
    MAINTAINABILITY(4, "Maintainability");

    /**
     * enum value
     */
    private Integer value;

    /**
     * enum description
     */
    private String desc;

    public static CategoryEnum fromName(String name) {
        if (name == null) return CODE_STYLE;
        String n = name.toUpperCase();
        for (CategoryEnum e : values()) {
            if (e.name().equals(n)) return e;
        }
        return CODE_STYLE;
    }

    public static CategoryEnum fromValue(Integer value) {
        if (value == null) return CODE_STYLE;
        for (CategoryEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return CODE_STYLE;
    }
}
