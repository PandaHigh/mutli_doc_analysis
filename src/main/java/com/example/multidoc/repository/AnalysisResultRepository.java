package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisResult;
import com.example.multidoc.model.AnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    Optional<AnalysisResult> findByTask(AnalysisTask task);

    // Added for task deletion
    @Transactional
    void deleteByTask(AnalysisTask task);
} 