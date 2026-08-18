package com.codeguardian.repository;

import com.codeguardian.entity.ReviewTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for review tasks.
 */
@Repository
public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {
    
    /**
     * Find tasks by status.
     */
    List<ReviewTask> findByStatus(Integer status);
    
    /**
     * Find tasks by review type.
     */
    Page<ReviewTask> findByReviewType(Integer reviewType, Pageable pageable);
    
    /**
     * Find tasks by fuzzy name match.
     */
    @Query("SELECT t FROM ReviewTask t WHERE t.name LIKE %:name%")
    Page<ReviewTask> findByNameContaining(@Param("name") String name, Pageable pageable);
    
    /**
     * Find tasks within a time range.
     */
    @Query("SELECT t FROM ReviewTask t WHERE t.createdAt BETWEEN :startTime AND :endTime")
    Page<ReviewTask> findByCreatedAtBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Combined query by name, type and time range.
     */
    @Query("SELECT t FROM ReviewTask t WHERE " +
           "(:name IS NULL OR t.name LIKE %:name%) AND " +
           "(:reviewType IS NULL OR t.reviewType = :reviewType) AND " +
           "(t.createdAt >= COALESCE(:startTime, t.createdAt)) AND " +
           "(t.createdAt <= COALESCE(:endTime, t.createdAt))")
    Page<ReviewTask> findByConditions(
            @Param("name") String name,
            @Param("reviewType") Integer reviewType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find a task by ID, eagerly loading its findings.
     * @deprecated use findById instead
     */
    default Optional<ReviewTask> findByIdWithFindings(@Param("id") Long id) {
        return findById(id);
    }

    /**
     * Get the most recently completed task.
     */
    Optional<ReviewTask> findTopByStatusOrderByCreatedAtDesc(Integer status);

    /**
     * Get the 5 most recently completed tasks.
     */
    List<ReviewTask> findTop5ByStatusOrderByCreatedAtDesc(Integer status);

    /**
     * Get the 5 most recently completed PROJECT-type tasks.
     */
    List<ReviewTask> findTop5ByStatusAndReviewTypeOrderByCreatedAtDesc(Integer status, Integer reviewType);

    /**
     * Get recently completed PROJECT-type tasks, de-duplicated by project name (latest wins).
     */
    @Query("SELECT t FROM ReviewTask t WHERE t.id IN (" +
           "  SELECT MAX(rt.id) FROM ReviewTask rt " +
           "  WHERE rt.status = :status AND rt.reviewType = :reviewType " +
           "  GROUP BY rt.name" +
           ") ORDER BY t.createdAt DESC")
    List<ReviewTask> findLatestProjectTasks(@Param("status") Integer status, @Param("reviewType") Integer reviewType, Pageable pageable);
}
