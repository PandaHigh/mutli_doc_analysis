package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
public class FieldRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    @ManyToOne
    @JoinColumn(name = "field_id")
    private ExcelField field;
    
    @Column(name = "field_name")
    private String fieldName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type")
    private RuleType ruleType;
    
    @Column(name = "rule_content", columnDefinition = "TEXT")
    private String ruleContent;
    
    @Column(columnDefinition = "TEXT")
    private String rule;
    
    private Integer priority; // 规则优先级，越高越重要
    
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

    public ExcelField getField() {
        return field;
    }

    public void setField(ExcelField field) {
        this.field = field;
    }
    
    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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
    
    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
} 