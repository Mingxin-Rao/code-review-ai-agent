package com.codeguardian.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Review type enum.
 *
 * @date 2025/12/30
 */
@Getter
@AllArgsConstructor
public enum ReviewTypeEnum {

    /**
     * Project
     */
    PROJECT(0, "Project"),

    /**
     * Directory
     */
    DIRECTORY(1, "Directory"),

    /**
     * File
     */
    FILE(2, "File"),

    /**
     * Code Snippet
     */
    SNIPPET(3, "Code Snippet"),

    /**
     * Git
     */
    GIT(4, "Git");

    /**
     * enum value
     */
    private Integer value;

    /**
     * enum description
     */
    private String desc;

    public static ReviewTypeEnum fromName(String name) {
        if (name == null) return SNIPPET;
        String n = name.toUpperCase();
        for (ReviewTypeEnum e : values()) {
            if (e.name().equals(n)) return e;
        }
        return SNIPPET;
    }

    public static ReviewTypeEnum fromValue(Integer value) {
        if (value == null) return SNIPPET;
        for (ReviewTypeEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return SNIPPET;
    }
}
