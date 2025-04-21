package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
@Table(name = "field_rule")
public class FieldRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    @Column(name = "field_names", columnDefinition = "TEXT")
    private String fieldNames; // 存储字段名称的JSON数组
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type")
    private RuleType ruleType;
    
    @Column(name = "rule_content", columnDefinition = "TEXT")
    private String ruleContent;
    
    @Column(name = "confidence")
    private Double confidence;
    
    public enum RuleType {
        EXPLICIT, // 显式规则
        IMPLICIT  // 隐含规则
    }

    // Default constructor
    public FieldRule() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalysisTask getTask() {
        return task;
    }

    public void setTask(AnalysisTask task) {
        this.task = task;
    }

    public String getFieldNames() {
        return fieldNames;
    }

    public void setFieldNames(String fieldNames) {
        this.fieldNames = fieldNames;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleContent() {
        return ruleContent;
    }

    public void setRuleContent(String ruleContent) {
        this.ruleContent = ruleContent;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
} 