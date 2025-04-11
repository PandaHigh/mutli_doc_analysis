package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"task_id", "field_name"})
})
public class ExcelField {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    @Column(name = "field_name")
    private String fieldName;
    
    @Column(name = "field_type")
    private String fieldType;
    
    private String category;
    
    @Column(length = 1000)
    private String description;
    
    // 存储从Word文档中提取的相关文本
    @Column(columnDefinition = "LONGTEXT")
    private String relatedText;
    
    @Column(columnDefinition = "TEXT")
    private String rules;

    // Default constructor
    public ExcelField() {
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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRelatedText() {
        return relatedText;
    }

    public void setRelatedText(String relatedText) {
        this.relatedText = relatedText;
    }
    
    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }
} 