package com.codeguardian.dto.integration;

import lombok.Data;

@Data
public class CicdTriggerRequest {
    private String gitUrl;
    private String branch;
    private String commitHash;
    private String triggerBy; // e.g., "JENKINS", "GITLAB_CI"
    private String projectPath; // optional, specifies a subdirectory
    private String blockOn; // blocking level
}
