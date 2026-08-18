package com.codeguardian.service;

import com.codeguardian.dto.ReviewRequestDTO;
import com.codeguardian.entity.Finding;
import com.codeguardian.enums.ModelProviderEnum;
import com.codeguardian.model.dto.SettingsDTO;
import com.codeguardian.repository.FindingRepository;
import com.codeguardian.repository.ReviewReportRepository;
import com.codeguardian.repository.ReviewTaskRepository;
import com.codeguardian.service.cache.SemanticFingerprintCacheService;
import com.codeguardian.service.rules.RuleEngineService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReviewServiceSemanticCacheHitTest {

    @Test
    void should_not_call_ai_when_cache_hit() throws Exception {
        ReviewTaskRepository taskRepository = mock(ReviewTaskRepository.class);
        FindingRepository findingRepository = mock(FindingRepository.class);
        ReviewReportRepository reportRepository = mock(ReviewReportRepository.class);
        AIModelService aiModelService = mock(AIModelService.class);
        CodeParserService codeParserService = mock(CodeParserService.class);
        RuleEngineService ruleEngineService = mock(RuleEngineService.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        GitService gitService = mock(GitService.class);
        SemanticFingerprintCacheService fingerprintCacheService = mock(SemanticFingerprintCacheService.class);

        SettingsDTO settings = new SettingsDTO();
        settings.setRuleCategories(java.util.Collections.emptyMap());
        when(configService.getSettings()).thenReturn(settings);

        Finding cachedFinding = new Finding();
        cachedFinding.setSeverity(1);
        cachedFinding.setTitle("cached");
        cachedFinding.setLocation("L1");
        cachedFinding.setDescription("d");

        when(fingerprintCacheService.tryGetCachedFindings(anyString(), anyString(), any(ModelProviderEnum.class), anyBoolean(), anyInt()))
                .thenReturn(Optional.of(List.of(cachedFinding)));

        ReviewService service = new ReviewService(
                taskRepository,
                findingRepository,
                reportRepository,
                aiModelService,
                codeParserService,
                ruleEngineService,
                configService,
                gitService,
                fingerprintCacheService
        );

        ReviewRequestDTO request = ReviewRequestDTO.builder()
                .rulesOnly(false)
                .modelProvider("QWEN")
                .enableRag(true)
                .build();

        Method method = ReviewService.class.getDeclaredMethod("executeReviewStrategy", String.class, String.class, ReviewRequestDTO.class);
        method.setAccessible(true);
        List<Finding> findings = (List<Finding>) method.invoke(service, "class A {}", "Java", request);

        assertEquals(1, findings.size());
        assertEquals("cached", findings.get(0).getTitle());
        verify(aiModelService, never()).reviewCode(anyString(), anyString(), anyString(), anyBoolean());
    }
}

