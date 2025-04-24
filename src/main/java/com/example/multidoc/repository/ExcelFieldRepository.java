package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.ExcelField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExcelFieldRepository extends JpaRepository<ExcelField, Long> {
    List<ExcelField> findByTask(AnalysisTask task);
    
    @Query("SELECT f FROM ExcelField f WHERE f.task = :task AND f.fieldName = :fieldName")
    Optional<ExcelField> findByTaskAndFieldName(@Param("task") AnalysisTask task, @Param("fieldName") String fieldName);
    
    @Query("SELECT f FROM ExcelField f WHERE f.task = :task AND f.tableName = :tableName AND f.fieldName = :fieldName")
    Optional<ExcelField> findByTaskAndTableNameAndFieldName(@Param("task") AnalysisTask task, @Param("tableName") String tableName, @Param("fieldName") String fieldName);
    
    @Query("SELECT f FROM ExcelField f WHERE f.task = :task AND f.category = :category")
    List<ExcelField> findByTaskAndCategory(@Param("task") AnalysisTask task, @Param("category") String category);
    
    @Query("SELECT DISTINCT f.category FROM ExcelField f WHERE f.task = :task AND f.category IS NOT NULL")
    List<String> findDistinctCategoriesByTask(@Param("task") AnalysisTask task);
    
    @Transactional
    void deleteByTask(AnalysisTask task);
} 