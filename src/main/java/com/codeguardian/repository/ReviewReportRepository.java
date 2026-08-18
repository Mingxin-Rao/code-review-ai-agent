package com.codeguardian.repository;

import com.codeguardian.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for review reports.
 */
@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    
    /**
     * Find the report for a task ID.
     */
    Optional<ReviewReport> findByTaskId(Long taskId);
}

