package com.codeguardian.dto.integration;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CicdStatusResponse {
    private Long taskId;
    private String status; // RUNNING, COMPLETED, FAILED
    private boolean passed; // whether it passed the quality gate
    private String message;
    private String reportUrl;
    private Summary summary;

    @Data
    @Builder
    public static class Summary {
        private int critical;
        private int high;
        private int medium;
        private int low;
    }
}
