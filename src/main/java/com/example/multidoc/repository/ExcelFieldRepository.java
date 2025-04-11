package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.ExcelField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExcelFieldRepository extends JpaRepository<ExcelField, Long> {
    List<ExcelField> findByTask(AnalysisTask task);
    Optional<ExcelField> findByTaskAndFieldName(AnalysisTask task, String fieldName);
    long countByTask(AnalysisTask task);
} 