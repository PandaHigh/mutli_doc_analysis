package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, String> {
    List<AnalysisTask> findByStatus(AnalysisTask.TaskStatus status);
    
    @Query("SELECT t FROM AnalysisTask t ORDER BY t.createdTime DESC")
    List<AnalysisTask> findAllOrderByCreatedTimeDesc();
    
    // Alias for findAllOrderByCreatedTimeDesc
    @Query("SELECT t FROM AnalysisTask t ORDER BY t.createdTime DESC")
    List<AnalysisTask> findAllByOrderByCreatedTimeDesc();
} 