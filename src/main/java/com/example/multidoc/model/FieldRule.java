package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_rules")
public class FieldRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private AnalysisTask task;
    
    @Column(name = "field_names", columnDefinition = "json", nullable = false)
    private String fieldNames; // JSON格式的字段名数组
    
    @Column(name = "rule_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;
    
    @Column(name = "rule_content", columnDefinition = "TEXT", nullable = false)
    private String ruleContent;
    
    @Column
    private Float confidence;
    
    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();
    
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

    public Float getConfidence() {
        return confidence;
    }

    public void setConfidence(Float confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "FieldRule{" +
                "id=" + id +
                ", fieldNames='" + fieldNames + '\'' +
                ", ruleType=" + ruleType +
                ", confidence=" + confidence +
                '}';
    }
} 