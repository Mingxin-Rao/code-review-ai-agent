package com.codeguardian.constants;

/**
 * Review-related constants
 */
public class ReviewConstants {
    
    /**
     * Review type
     */
    public static class ReviewType {
        public static final String PROJECT = "PROJECT";
        public static final String DIRECTORY = "DIRECTORY";
        public static final String FILE = "FILE";
        public static final String SNIPPET = "SNIPPET";
        public static final String GIT = "GIT";
    }
    
    /**
     * Task status
     */
    public static class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }
    
    /**
     * Issue severity
     */
    public static class Severity {
        public static final String CRITICAL = "CRITICAL";
        public static final String HIGH = "HIGH";
        public static final String MEDIUM = "MEDIUM";
        public static final String LOW = "LOW";
    }
    
    /**
     * Issue category
     */
    public static class Category {
        public static final String SECURITY = "SECURITY";
        public static final String PERFORMANCE = "PERFORMANCE";
        public static final String BUG = "BUG";
        public static final String CODE_STYLE = "CODE_STYLE";
        public static final String MAINTAINABILITY = "MAINTAINABILITY";
    }
}

