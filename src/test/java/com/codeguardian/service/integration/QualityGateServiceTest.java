package com.codeguardian.service.integration;

import com.codeguardian.entity.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityGateServiceTest {

    private final QualityGateService service = new QualityGateService(null);

    @Test
    void should_pass_when_no_findings() {
        assertTrue(service.checkQualityGate(List.of(), "CRITICAL"));
    }

    @Test
    void should_block_when_critical_found() {
        List<Finding> findings = List.of(
                Finding.builder().severity(com.codeguardian.enums.SeverityEnum.CRITICAL.getValue()).build()
        );
        assertFalse(service.checkQualityGate(findings, "CRITICAL"));
    }

    @Test
    void should_pass_when_critical_found_but_blocking_on_none() {
        List<Finding> findings = List.of(
                Finding.builder().severity(com.codeguardian.enums.SeverityEnum.CRITICAL.getValue()).build()
        );
        // If blockOn is null or unknown, it passes (based on implementation logic)
        assertTrue(service.checkQualityGate(findings, null)); 
    }

    @Test
    void should_block_when_medium_found_and_blocking_on_medium() {
        List<Finding> findings = List.of(
                Finding.builder().severity(com.codeguardian.enums.SeverityEnum.MEDIUM.getValue()).build()
        );
        assertFalse(service.checkQualityGate(findings, "MEDIUM"));
    }
    
    @Test
    void should_block_when_high_found_and_blocking_on_low() {
        // If blocking on LOW, any LOW, MEDIUM, HIGH, CRITICAL should block
        List<Finding> findings = List.of(
                Finding.builder().severity(com.codeguardian.enums.SeverityEnum.HIGH.getValue()).build()
        );
        assertFalse(service.checkQualityGate(findings, "LOW"));
    }
}
