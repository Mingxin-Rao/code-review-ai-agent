package com.codeguardian.task;

import com.codeguardian.dto.DashboardDTO;
import com.codeguardian.service.DashboardService;
import com.codeguardian.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that aggregates dashboard data.
 *
 * <p>Periodically computes the dashboard aggregates and caches them in the system config, so pages do not have to compute them on every request.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardScheduler {

    private final DashboardService dashboardService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    /**
     * Periodically refresh the cached dashboard data.
     *
     * <p>Runs every 5 minutes by default; override with the {@code dashboard.refresh.interval.ms} property.</p>
     */
    @Scheduled(fixedDelayString = "${dashboard.refresh.interval.ms:300000}")
    public void refreshDashboardCache() {
        try {
            log.info("Dashboard metrics cache start");
            DashboardDTO dto = dashboardService.computeDashboardData();
            String json = objectMapper.writeValueAsString(dto);
            systemConfigService.saveRawConfig(
                    SystemConfigService.KEY_METRICS_DASHBOARD,
                    json,
                    "Metrics",
                    "Dashboard aggregated metrics cache"
            );
            log.info("Dashboard metrics cache updated");
        } catch (Exception e) {
            log.warn("Failed to refresh dashboard cache: {}", e.getMessage());
        }
    }
}

