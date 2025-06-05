package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.DocumentScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentScopeRepository extends JpaRepository<DocumentScope, Long> {
    List<DocumentScope> findByTask(AnalysisTask task);
    void deleteByTask(AnalysisTask task);
} 