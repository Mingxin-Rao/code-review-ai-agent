package com.codeguardian.repository;

import com.codeguardian.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for review findings.
 */
@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {
    
    /**
     * Find all findings for a task ID.
     */
    List<Finding> findByTaskId(Long taskId);
    
    /**
     * Find findings by task ID and severity.
     */
    List<Finding> findByTaskIdAndSeverity(Long taskId, Integer severity);
    
    /**
     * Find findings by task ID and category code.
     */
    List<Finding> findByTaskIdAndCategory(Long taskId, String category);
    
    /**
     * Count the issues for a task.
     */
    @Query("SELECT COUNT(f) FROM Finding f WHERE f.taskId = :taskId")
    Long countByTaskId(@Param("taskId") Long taskId);
    
    /**
     * Count the critical issues for a task.
     */
    @Query("SELECT COUNT(f) FROM Finding f WHERE f.taskId = :taskId AND f.severity = :severity")
    Long countByTaskIdAndSeverity(@Param("taskId") Long taskId, @Param("severity") Integer severity);

    /**
     * Delete all findings for a task ID.
     */
    void deleteByTaskId(Long taskId);
}
