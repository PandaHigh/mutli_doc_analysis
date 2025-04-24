package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, Long> {
    
    List<TaskLog> findByTask(AnalysisTask task);
    
    @Query("SELECT t FROM TaskLog t WHERE t.task = :task ORDER BY t.logTime DESC")
    List<TaskLog> findByTaskOrderByLogTimeDesc(@Param("task") AnalysisTask task);
    
    @Query("SELECT t FROM TaskLog t WHERE t.task = :task AND t.logLevel = :logLevel ORDER BY t.logTime DESC")
    List<TaskLog> findByTaskAndLogLevelOrderByLogTimeDesc(@Param("task") AnalysisTask task, @Param("logLevel") String logLevel);
    
    @Query("SELECT t FROM TaskLog t WHERE t.task.id = :taskId ORDER BY t.logTime ASC")
    List<TaskLog> findByTaskIdOrderByTimestampAsc(@Param("taskId") String taskId);
    
    void deleteByTask(AnalysisTask task);
} 