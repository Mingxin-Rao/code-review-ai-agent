package com.codeguardian.service;

import com.codeguardian.dto.ReviewRequestDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReviewServiceScopeFormattingTest {

    private String invokeDetermineScope(ReviewRequestDTO req) throws Exception {
        ReviewService svc = new ReviewService(null, null, null, null, null, null, null, null, null);
        Method m = ReviewService.class.getDeclaredMethod("determineScope", ReviewRequestDTO.class);
        m.setAccessible(true);
        return (String) m.invoke(svc, req);
    }

    @Test
    void should_return_entire_project_when_project_review() throws Exception {
        ReviewRequestDTO req = ReviewRequestDTO.builder()
                .reviewType("PROJECT")
                .projectPath("/work/app")
                .build();
        String scope = invokeDetermineScope(req);
        assertEquals("Whole Project", scope);
    }

    @Test
    void should_return_specified_directory_when_directory_review() throws Exception {
        ReviewRequestDTO req = ReviewRequestDTO.builder()
                .reviewType("DIRECTORY")
                .directoryPath("/work/app/src")
                .build();
        String scope = invokeDetermineScope(req);
        assertEquals("Directory", scope);
    }

    @Test
    void should_return_specified_file_when_file_review() throws Exception {
        ReviewRequestDTO req = ReviewRequestDTO.builder()
                .reviewType("FILE")
                .projectPath("/work/app")
                .filePath("/work/app/src/main/java/App.java")
                .build();
        String scope = invokeDetermineScope(req);
        assertEquals("File", scope);
    }
}
