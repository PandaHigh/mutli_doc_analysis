package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldRelationRepository extends JpaRepository<FieldRelation, Long> {
    List<FieldRelation> findByTask(AnalysisTask task);
    List<FieldRelation> findBySourceField(ExcelField sourceField);
    List<FieldRelation> findByTaskAndSourceFieldOrderByRelationScoreDesc(AnalysisTask task, ExcelField sourceField);
    List<FieldRelation> findByTaskAndSourceField(AnalysisTask task, ExcelField sourceField);
    List<FieldRelation> findByTaskAndTargetField(AnalysisTask task, ExcelField targetField);
    long countByTask(AnalysisTask task);
} 