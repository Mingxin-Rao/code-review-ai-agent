package com.codeguardian.service.integration;

import com.codeguardian.entity.Finding;
import com.codeguardian.repository.FindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quality gate service
 * Determines whether review results meet the passing criteria
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QualityGateService {

    private final FindingRepository findingRepository;

    /**
     * Check whether the quality gate passes
     *
     * @param task the review task
     * @param blockOn blocking level (CRITICAL, HIGH, MEDIUM, LOW)
     * @return true if passed, false if blocked
     */
    public boolean checkQualityGate(Long taskId, String blockOn) {
        if (taskId == null) {
            return true;
        }

        List<Finding> findings = findingRepository.findByTaskId(taskId);
        return checkQualityGate(findings, blockOn, taskId);
    }

    boolean checkQualityGate(List<Finding> findings, String blockOn) {
        return checkQualityGate(findings, blockOn, null);
    }

    private boolean checkQualityGate(List<Finding> findings, String blockOn, Long taskId) {
        Map<Integer, Long> counts = findings.stream()
                .filter(f -> f.getSeverity() != null)
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));

        long critical = counts.getOrDefault(com.codeguardian.enums.SeverityEnum.CRITICAL.getValue(), 0L);
        long high = counts.getOrDefault(com.codeguardian.enums.SeverityEnum.HIGH.getValue(), 0L);
        long medium = counts.getOrDefault(com.codeguardian.enums.SeverityEnum.MEDIUM.getValue(), 0L);
        long low = counts.getOrDefault(com.codeguardian.enums.SeverityEnum.LOW.getValue(), 0L);

        log.info("Quality gate check: taskId={}, blockOn={}, counts={C:{}, H:{}, M:{}, L:{}}",
                taskId, blockOn, critical, high, medium, low);

        if (blockOn == null || blockOn.isBlank()) {
            return true; // do not block by default
        }

        switch (blockOn.toUpperCase()) {
            case "LOW":
                if (low > 0) return false;
                // fallthrough
            case "MEDIUM":
                if (medium > 0) return false;
                // fallthrough
            case "HIGH":
                if (high > 0) return false;
                // fallthrough
            case "CRITICAL":
                if (critical > 0) return false;
                break;
            default:
                log.warn("Unknown blocking level: {}; ignoring the blocking policy", blockOn);
                return true;
        }

        return true;
    }
}
