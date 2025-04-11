package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldRuleRepository extends JpaRepository<FieldRule, Long> {
    List<FieldRule> findByTask(AnalysisTask task);
    List<FieldRule> findByField(ExcelField field);
    List<FieldRule> findByFieldAndRuleType(ExcelField field, FieldRule.RuleType ruleType);
    List<FieldRule> findByTaskAndFieldOrderByPriorityDesc(AnalysisTask task, ExcelField field);
    long countByTask(AnalysisTask task);
    long countByTaskAndField(AnalysisTask task, ExcelField field);
} 