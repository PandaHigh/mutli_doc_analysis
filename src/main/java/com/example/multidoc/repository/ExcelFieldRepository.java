package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.ExcelField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExcelFieldRepository extends JpaRepository<ExcelField, Long> {
    List<ExcelField> findByTask(AnalysisTask task);
    Optional<ExcelField> findByTaskAndTableNameAndFieldName(AnalysisTask task, String tableName, String fieldName);
    @Transactional
    void deleteByTask(AnalysisTask task);
} 