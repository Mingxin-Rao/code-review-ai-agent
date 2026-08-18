package com.codeguardian.service;

import com.codeguardian.dto.DashboardDTO;
import com.codeguardian.entity.Finding;
import com.codeguardian.entity.ReviewTask;
import com.codeguardian.repository.FindingRepository;
import com.codeguardian.repository.ReviewTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final FindingRepository findingRepository;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    /**
     * Compute and generate the latest dashboard data
     *
     * @return the latest dashboard data
     */
    @Transactional(readOnly = true)
    public DashboardDTO computeDashboardData() {
        Optional<ReviewTask> latestTaskOpt = reviewTaskRepository.findTopByStatusOrderByCreatedAtDesc(com.codeguardian.enums.TaskStatusEnum.COMPLETED.getValue());
        
        int healthScore = 100;
        DashboardDTO.SeverityDistribution distribution = DashboardDTO.SeverityDistribution.builder()
                .critical(0).high(0).medium(0).low(0)
                .build();

        if (latestTaskOpt.isPresent()) {
            ReviewTask latestTask = latestTaskOpt.get();
            List<Finding> findings = findingRepository.findByTaskId(latestTask.getId());
            
            for (Finding f : findings) {
                Integer severity = f.getSeverity();
                if (severity == null) continue;
                if (severity.equals(com.codeguardian.enums.SeverityEnum.CRITICAL.getValue())) distribution.setCritical(distribution.getCritical() + 1);
                else if (severity.equals(com.codeguardian.enums.SeverityEnum.HIGH.getValue())) distribution.setHigh(distribution.getHigh() + 1);
                else if (severity.equals(com.codeguardian.enums.SeverityEnum.MEDIUM.getValue())) distribution.setMedium(distribution.getMedium() + 1);
                else distribution.setLow(distribution.getLow() + 1);
            }
            
            healthScore = calculateHealthScore(distribution);
        }

        // get the 5 most recent completed project-type tasks for project statistics (only the latest per project)
        List<ReviewTask> recentTasks = reviewTaskRepository
                .findLatestProjectTasks(com.codeguardian.enums.TaskStatusEnum.COMPLETED.getValue(), com.codeguardian.enums.ReviewTypeEnum.PROJECT.getValue(), org.springframework.data.domain.PageRequest.of(0, 5));
        List<DashboardDTO.ProjectStatDTO> projectStats = recentTasks.stream()
                .map(this::convertToProjectStat)
                .collect(Collectors.toList());
        
        // reverse the list so the chart shows chronological order from left to right (old -> new)
        Collections.reverse(projectStats);

        return DashboardDTO.builder()
                .healthScore(healthScore)
                .problemDistribution(distribution)
                .projectStats(projectStats)
                .build();
    }

    /**
     * Calculate the code health score based on the severity distribution
     *
     * @param distribution the severity distribution
     * @return a health score from 0 to 100
     */
    private int calculateHealthScore(DashboardDTO.SeverityDistribution distribution) {
        int penalty = distribution.getCritical() * 10
                + distribution.getHigh() * 5
                + distribution.getMedium() * 2
                + distribution.getLow() * 1;
        return Math.max(0, 100 - penalty);
    }

    /**
     * Get the cached dashboard data; if absent, compute and return it in real time
     *
     * @return the dashboard data
     */
    public DashboardDTO getCachedDashboardData() {
        try {
            String json = systemConfigService.getConfigValue(SystemConfigService.KEY_METRICS_DASHBOARD);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, DashboardDTO.class);
            }
        } catch (Exception ignored) {
        }
        return computeDashboardData();
    }

    private DashboardDTO.ProjectStatDTO convertToProjectStat(ReviewTask task) {
        List<Finding> findings = findingRepository.findByTaskId(task.getId());
        
        int critical = 0, high = 0, medium = 0, low = 0;
        for (Finding f : findings) {
            Integer severity = f.getSeverity();
            if (severity == null) continue;
            if (severity.equals(com.codeguardian.enums.SeverityEnum.CRITICAL.getValue())) critical++;
            else if (severity.equals(com.codeguardian.enums.SeverityEnum.HIGH.getValue())) high++;
            else if (severity.equals(com.codeguardian.enums.SeverityEnum.MEDIUM.getValue())) medium++;
            else low++;
        }
        return DashboardDTO.ProjectStatDTO.builder()
                .projectName(task.getName())
                .criticalCount(critical)
                .highCount(high)
                .mediumCount(medium)
                .lowCount(low)
                .totalCount(findings != null ? findings.size() : 0)
                .build();
    }
}
