package com.example.multidoc.repository;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.FieldRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FieldRuleRepository extends JpaRepository<FieldRule, Long> {
    List<FieldRule> findByTask(AnalysisTask task);
    List<FieldRule> findByTaskAndRuleType(AnalysisTask task, FieldRule.RuleType ruleType);
    long countByTask(AnalysisTask task);

    @Transactional
    void deleteByTask(AnalysisTask task);
} 