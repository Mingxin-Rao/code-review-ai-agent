package com.codeguardian.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Task status enum.
 *
 * @date 2025/12/30
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    /**
     * Pending
     */
    PENDING(0, "Pending"),

    /**
     * Running
     */
    RUNNING(1, "Running"),

    /**
     * Completed
     */
    COMPLETED(2, "Completed"),

    /**
     * Failed
     */
    FAILED(3, "Failed");

    /**
     * enum value
     */
    private Integer value;

    /**
     * enum description
     */
    private String desc;

    public static TaskStatusEnum fromName(String name) {
        if (name == null) return PENDING;
        String n = name.toUpperCase();
        for (TaskStatusEnum e : values()) {
            if (e.name().equals(n)) return e;
        }
        return PENDING;
    }

    public static TaskStatusEnum fromValue(Integer value) {
        if (value == null) return PENDING;
        for (TaskStatusEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return PENDING;
    }
}
