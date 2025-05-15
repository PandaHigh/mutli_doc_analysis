package com.example.multidoc.repository;

import com.example.multidoc.model.RuleValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface RuleValidationResultRepository extends JpaRepository<RuleValidationResult, String> {
    
    Optional<RuleValidationResult> findByTaskId(String taskId);
    
    List<RuleValidationResult> findByTaskIdOrderByStartTimeDesc(String taskId);
    
    @Query("SELECT r FROM RuleValidationResult r WHERE r.taskId = :taskId ORDER BY r.startTime DESC")
    List<RuleValidationResult> findLatestByTaskId(@Param("taskId") String taskId);
    
    @Query(value = "SELECT * FROM rule_validation_results WHERE task_id = :taskId ORDER BY start_time DESC LIMIT 1", nativeQuery = true)
    Optional<RuleValidationResult> findLatestResultByTaskId(@Param("taskId") String taskId);
} 